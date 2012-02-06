/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.webdav;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DavMethod;
import org.apache.jackrabbit.webdav.client.methods.LockMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.lock.LockDiscovery;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Jackrabbit includes a WebDAV client library. Let's use it to test our
 * server.
 */

public class JackrabbitWebdavClientTest extends AbstractServerTest {

    private static String USERNAME = "userId";
    
    private static HttpClient client;

    @BeforeClass
    public static void setUp() {
        // Setup code
        client = createClient(USERNAME);
    }

    protected static HttpClient createClient(String username) {
        HostConfiguration hostConfig = new HostConfiguration();
        hostConfig.setHost("localhost", PORT);

        HttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        int maxHostConnections = 20;
        params.setMaxConnectionsPerHost(hostConfig, maxHostConnections);
        connectionManager.setParams(params);

        HttpClient httpClient = new HttpClient(connectionManager);
        httpClient.setHostConfiguration(hostConfig);

        Credentials creds = new UsernamePasswordCredentials(username, "pw");
        httpClient.getState().setCredentials(AuthScope.ANY, creds);
        return httpClient;
    }

    @Test
    public void testPropFindOnFolderDepthInfinity() throws Exception {
        DavMethod pFind = new PropFindMethod(ROOT_URI, DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_INFINITY);
        client.executeMethod(pFind);

        MultiStatus multiStatus = pFind.getResponseBodyAsMultiStatus();

        //Not quite nice, but for a example ok
        DavPropertySet props = multiStatus.getResponses()[0].getProperties(200);

        for (DavPropertyName propName : props.getPropertyNames()) {
            // System.out.println(propName + "  " + props.get(propName).getValue());
        }
    }

    @Test
    public void testPropFindOnFolderDepthZero() throws Exception {
        DavMethod pFind = new PropFindMethod(ROOT_URI, DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_0);
        client.executeMethod(pFind);

        MultiStatus multiStatus = pFind.getResponseBodyAsMultiStatus();

        //Not quite nice, but for a example ok
        DavPropertySet props = multiStatus.getResponses()[0].getProperties(200);

        for (DavPropertyName propName : props.getPropertyNames()) {
            // System.out.println(propName + "  " + props.get(propName).getValue());
        }
    }

    @Test
    public void testListFolderContents() throws Exception {
        DavMethod pFind = new PropFindMethod(ROOT_URI, DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_1);
        client.executeMethod(pFind);

        MultiStatus multiStatus = pFind.getResponseBodyAsMultiStatus();
        MultiStatusResponse[] responses = multiStatus.getResponses();
        assertTrue(responses.length >= 4);

        boolean found = false;
        for (MultiStatusResponse response : responses) {
            if (response.getHref().endsWith("quality.jpg")) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testGetDocProperties() throws Exception {
        DavMethod pFind = new PropFindMethod(
                ROOT_URI + "quality.jpg", DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_1);
        client.executeMethod(pFind);

        MultiStatus multiStatus = pFind.getResponseBodyAsMultiStatus();
        MultiStatusResponse[] responses = multiStatus.getResponses();
        assertEquals(1L, (long) responses.length);

        MultiStatusResponse response = responses[0];
        assertEquals("123631", response.getProperties(200).get("getcontentlength").getValue());
    }

    @Test
    public void testGetFolderPropertiesAcceptTextXml() throws Exception {
        checkAccept("text/xml");
    }

    @Test
    public void testGetFolderPropertiesAcceptTextMisc() throws Exception {
        checkAccept("text/html, image/jpeg;q=0.9, image/png;q=0.9, text/*;q=0.9, image/*;q=0.9, */*;q=0.8");
    }

    protected void checkAccept(String accept) throws Exception {
        DavMethod pFind = new PropFindMethod(ROOT_URI,
                DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_0);
        pFind.setRequestHeader("Accept", accept);
        client.executeMethod(pFind);

        MultiStatus multiStatus = pFind.getResponseBodyAsMultiStatus();
        MultiStatusResponse[] responses = multiStatus.getResponses();
        assertEquals(1, responses.length);

        MultiStatusResponse response = responses[0];
        assertEquals(
                "workspace",
                response.getProperties(200).get(
                        DavConstants.PROPERTY_DISPLAYNAME).getValue());
    }
    
    @Test
    public void testPropFindOnLockedFile() throws Exception {
        String fileUri = ROOT_URI + "quality.jpg";
        DavMethod pLock = new LockMethod(
                fileUri, Scope.EXCLUSIVE, Type.WRITE, USERNAME, 10000l, false);
        client.executeMethod(pLock);
        pLock.checkSuccess();
        
        HttpClient client2 = createClient("user2Id");
        DavMethod pFind = new PropFindMethod(
                fileUri, DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_1);
        client2.executeMethod(pFind);

        MultiStatus multiStatus = pFind.getResponseBodyAsMultiStatus();
        MultiStatusResponse[] responses = multiStatus.getResponses();
        assertEquals(1L, (long) responses.length);

        MultiStatusResponse response = responses[0];
        DavProperty<?> pLockDiscovery =  
                response.getProperties(200).get(DavConstants.PROPERTY_LOCKDISCOVERY);
        Element eLockDiscovery = 
                (Element) ((Element) pLockDiscovery.getValue()).getParentNode();
        LockDiscovery lockDiscovery = LockDiscovery.createFromXml(eLockDiscovery);
        assertEquals("system", lockDiscovery.getValue().get(0).getOwner());
    }

}
