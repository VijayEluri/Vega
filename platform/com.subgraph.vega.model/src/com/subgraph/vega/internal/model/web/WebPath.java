package com.subgraph.vega.internal.model.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import com.db4o.ObjectContainer;
import com.db4o.activation.ActivationPurpose;
import com.db4o.collections.ActivatableArrayList;
import com.db4o.collections.ActivatableHashMap;
import com.google.common.base.Objects;
import com.subgraph.vega.api.events.EventListenerManager;
import com.subgraph.vega.api.model.web.IWebEntity;
import com.subgraph.vega.api.model.web.IWebMountPoint;
import com.subgraph.vega.api.model.web.IWebPath;
import com.subgraph.vega.api.model.web.IWebPathParameters;
import com.subgraph.vega.api.model.web.IWebResponse;

public class WebPath extends WebEntity implements IWebPath {
	
	static WebPath createRootPath(EventListenerManager eventManager, ObjectContainer database) {
		return new WebPath(eventManager, database, null, "");
	}
	
	private final WebPath parentPath;
	private final String pathComponent;
	private final Map<String, WebPath> childPathMap = new ActivatableHashMap<String, WebPath>();
	
	private IWebMountPoint mountPoint;
	private final WebPathParameters getParameters = new WebPathParameters();
	private final WebPathParameters postParameters = new WebPathParameters();
	
	private final List<IWebResponse> getResponses = new ActivatableArrayList<IWebResponse>();
	private final List<IWebResponse> postResponses = new ActivatableArrayList<IWebResponse>();
	
	private PathType pathType;
		
	private transient URI cachedUri;
	private transient String cachedFullPath;
	
	private WebPath(EventListenerManager eventManager, ObjectContainer database, WebPath parentPath, String pathComponent) {
		this(eventManager, database, parentPath, pathComponent, null);
	}
	
	WebPath(EventListenerManager eventManager, ObjectContainer database, WebPath parentPath, String pathComponent, IWebMountPoint mountPoint) {
		super(eventManager, database);
		this.parentPath = parentPath;
		this.pathComponent = pathComponent;
		this.mountPoint = mountPoint;
		this.pathType = PathType.PATH_UNKNOWN;
	}
	
	@Override
	public WebPath getParentPath() {
		return parentPath;
	}
	
	@Override
	public URI getUri() {
		activate(ActivationPurpose.READ);
		synchronized(this) {
			if(cachedUri == null) 
				cachedUri = generateURI();
			return cachedUri;
		}
	}
	
	private URI generateURI() {
		final URI hostUri = mountPoint.getWebHost().getUri();
		return hostUri.resolve(getFullPath());
	}

	public String getFullPath() {
		if(cachedFullPath == null) 
			cachedFullPath = generateFullPath();
		return cachedFullPath;
	}
	
	private String generateFullPath() {
		activate(ActivationPurpose.READ);
		if(parentPath == null)
			return "/";
		
		final String parentFullPath = parentPath.getFullPath();
		if(parentFullPath.endsWith("/"))
			return parentFullPath + pathComponent;
		else
			return parentFullPath + "/" + pathComponent;
	}
	
	@Override
	public IWebMountPoint getMountPoint() {
		activate(ActivationPurpose.READ);
		return mountPoint;
	}

	@Override
	public Collection<IWebPath> getChildPaths() {
		activate(ActivationPurpose.READ);
		synchronized(childPathMap) {
			return new HashSet<IWebPath>(childPathMap.values());
		}
	}
	
	void setMountPoint(IWebMountPoint mountPoint) {
		activate(ActivationPurpose.READ);
		this.mountPoint = mountPoint;
	}
	
	@Override
	public boolean equals(Object other) {
		if(this == other) {
			return true;
		} else if(other instanceof WebPath) {
			WebPath that = (WebPath) other;
			return this.getMountPoint().getWebHost().equals(that.getMountPoint().getWebHost()) &&
				this.getUri().getPath().equals(that.getUri().getPath());
		} else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(this.getMountPoint().getWebHost(), this.getUri().getPath());
	}
	
	@Override
	public String toString() {
		return Objects.toStringHelper(this).add("host", mountPoint.getWebHost()).add("path", getFullPath()).toString();
	}

	@Override
	public boolean isGetTarget() {
		activate(ActivationPurpose.READ);
		return getParameters.hasParameters();
	}

	@Override
	public boolean isPostTarget() {
		activate(ActivationPurpose.READ);
		return postParameters.hasParameters();
	}

	@Override
	public void addGetParameterList(List<NameValuePair> params) {
		activate(ActivationPurpose.READ);
		getParameters.addParameterList(params);
	}

	@Override
	public void addPostParameterList(List<NameValuePair> params) {
		activate(ActivationPurpose.READ);
		postParameters.addParameterList(params);
	}

	@Override
	public IWebPathParameters getGetParameters() {
		activate(ActivationPurpose.READ);
		return getParameters;
	}

	@Override
	public IWebPathParameters getPostParameters() {
		activate(ActivationPurpose.READ);
		return postParameters;
	}
	
	@Override
	public IWebPath getChildPath(String pathComponent) {
		activate(ActivationPurpose.READ);
		synchronized(childPathMap) {
			return childPathMap.get(pathComponent);
		}
	}
	
	@Override
	public WebPath addChildPath(String pathComponent) {
		activate(ActivationPurpose.READ);
		synchronized(childPathMap) {
			if(childPathMap.containsKey(pathComponent)) 	
				return childPathMap.get(pathComponent);
			
			WebPath newPath = new WebPath(eventManager, database, this, pathComponent, getMountPoint());

			ObjectContainer database = getDatabase();
			synchronized(database) {
				database.store(newPath);
			}
			
			newPath.setDatabase(database);
			childPathMap.put(pathComponent, newPath);
			notifyNewEntity(newPath);
			return newPath;
		}
	}

	@Override
	public String getPathComponent() {
		activate(ActivationPurpose.READ);
		return pathComponent;
	}

	@Override
	public List<IWebResponse> getGetResponses() {
		activate(ActivationPurpose.READ);
		synchronized(getResponses) {
			return Collections.unmodifiableList(new ArrayList<IWebResponse>(getResponses));
		}
	}

	@Override
	public List<IWebResponse> getPostResponses() {
		activate(ActivationPurpose.READ);
		synchronized(postResponses) {
			return Collections.unmodifiableList(new ArrayList<IWebResponse>(postResponses));
		}
	}

	@Override
	public IWebEntity getParent() {
		activate(ActivationPurpose.READ);
		if(parentPath != null)
			return parentPath;
		else if(mountPoint != null)
			return mountPoint.getWebHost();
		else
			return null;
	}

	@Override
	public void addPostResponse(List<NameValuePair> parameters, String mimeType) {
		activate(ActivationPurpose.READ);
		synchronized(postResponses) {
			postResponses.add(createWebResponse(parameters, mimeType));
		}
	}
	
	private WebResponse createWebResponse(List<NameValuePair> parameters, String mimeType) {
		WebResponse response = new WebResponse(eventManager, database, this, parameters, mimeType);
		ObjectContainer database = getDatabase();
		synchronized(database) {
			database.store(response);
		}
		response.setDatabase(getDatabase());
		return response;
	}

	@Override
	public void addGetResponse(String query, String mimeType) {
		activate(ActivationPurpose.READ);
		synchronized(getResponses) {
			getResponses.add(createWebResponse(parseParameters(query), mimeType));
		}		
	}
	
	private static List<NameValuePair> parseParameters(String query) {
		if(query == null || query.isEmpty())
			return Collections.emptyList();
		final List<NameValuePair> parameterList = new ArrayList<NameValuePair>();
		try {
			URLEncodedUtils.parse(parameterList, new Scanner(query), "UTF-8");
		} catch (RuntimeException e) {
			parameterList.clear();
			parameterList.add(new BasicNameValuePair(query, null));
		}
		return parameterList;
	}

	@Override
	public void setPathType(PathType type) {
		activate(ActivationPurpose.WRITE);
		this.pathType = type;
		cachedFullPath = null;
		cachedUri = null;
	}

	@Override
	public PathType getPathType() {
		activate(ActivationPurpose.READ);
		return pathType;
	}
}