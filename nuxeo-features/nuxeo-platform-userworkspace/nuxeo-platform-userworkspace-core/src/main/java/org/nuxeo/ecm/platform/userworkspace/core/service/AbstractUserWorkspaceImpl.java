/*
 * (C) Copyright 2006-2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */
package org.nuxeo.ecm.platform.userworkspace.core.service;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.IdUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.pathsegment.PathSegmentService;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.platform.usermanager.UserAdapter;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.ecm.platform.userworkspace.constants.UserWorkspaceConstants;
import org.nuxeo.runtime.api.Framework;

/**
 * Abstract class holding most of the logic for using {@link UnrestrictedSessionRunner} while creating UserWorkspaces
 * and associated resources
 *
 * @author tiry
 * @since 5.9.5
 */
public abstract class AbstractUserWorkspaceImpl implements UserWorkspaceService {

    private static final Log log = LogFactory.getLog(DefaultUserWorkspaceServiceImpl.class);

    private static final long serialVersionUID = 1L;

    protected String targetDomainName;

    protected final int maxsize;

    public AbstractUserWorkspaceImpl() {
        super();
        maxsize = Framework.getService(PathSegmentService.class)
                .getMaxSize();
    }

    protected String getDomainName(CoreSession userCoreSession, DocumentModel currentDocument) {
        if (targetDomainName == null) {
            RootDomainFinder finder = new RootDomainFinder(userCoreSession);
            finder.runUnrestricted();
            targetDomainName = finder.domaineName;
        }
        return targetDomainName;
    }

    protected String getUserWorkspaceNameForUser(String username) {
        return IdUtils.generateId(username, "-", false, maxsize);
    }

    protected String digest(String username, int maxsize) {
        try {
            MessageDigest crypt = MessageDigest.getInstance("SHA-1");
            crypt.update(username.getBytes());
            return new String(Hex.encodeHex(crypt.digest())).substring(0, maxsize);
        } catch (NoSuchAlgorithmException cause) {
            throw new NuxeoException("Cannot digest " + username, cause);
        }
    }

    protected String computePathUserWorkspaceRoot(CoreSession userCoreSession, String usedUsername,
            DocumentModel currentDocument) {
        String domainName = getDomainName(userCoreSession, currentDocument);
        if (domainName == null) {
            throw new NuxeoException("Unable to find root domain for UserWorkspace");
        }
        return new Path("/" + domainName)
                .append(UserWorkspaceConstants.USERS_PERSONAL_WORKSPACES_ROOT)
                .toString();
    }

    @Override
    public DocumentModel getCurrentUserPersonalWorkspace(String userName, DocumentModel currentDocument) {
        if (currentDocument == null) {
            return null;
        }
        return getCurrentUserPersonalWorkspace(null, userName, currentDocument.getCoreSession(), currentDocument);
    }

    @Override
    public DocumentModel getCurrentUserPersonalWorkspace(CoreSession userCoreSession, DocumentModel context) {
        return getCurrentUserPersonalWorkspace(userCoreSession.getPrincipal(), null, userCoreSession, context);
    }

    /**
     * This method handles the UserWorkspace creation with a Principal or a username. At least one should be passed. If
     * a principal is passed, the username is not taken into account.
     *
     * @since 5.7 "userWorkspaceCreated" is triggered
     */
    protected DocumentModel getCurrentUserPersonalWorkspace(Principal principal, String userName,
            CoreSession userCoreSession, DocumentModel context) {
        if (principal == null && StringUtils.isEmpty(userName)) {
            return null;
        }

        String usedUsername;
        if (principal instanceof NuxeoPrincipal) {
            usedUsername = ((NuxeoPrincipal) principal).getActingUser();
        } else {
            usedUsername = userName;
        }
        if (NuxeoPrincipal.isTransientUsername(usedUsername)) {
            // no personal workspace for transient users
            return null;
        }

        PathRef rootref = getExistingUserWorkspaceRoot(userCoreSession, usedUsername, context);
        PathRef uwref = getExistingUserWorkspace(userCoreSession, rootref, principal, usedUsername);
        DocumentModel uw = userCoreSession.getDocument(uwref);

        return uw;
    }

    protected PathRef getExistingUserWorkspaceRoot(CoreSession session, String username, DocumentModel context) {
        PathRef rootref = new PathRef(computePathUserWorkspaceRoot(session, username, context));
        if (session.exists(rootref)) {
            return rootref;
        }
        return new PathRef(new UnrestrictedRootCreator(rootref, username, session).create().getPathAsString());
    }

    protected PathRef getExistingUserWorkspace(CoreSession session, PathRef rootref, Principal principal, String username) {
        String workspacename = getUserWorkspaceNameForUser(username);
        PathRef uwref = resolveUserWorkspace(session, rootref, username, workspacename, maxsize);
        if (session.exists(uwref)) {
            return uwref;
        }
        PathRef uwcompatref = resolveUserWorkspace(session, rootref, username, IdUtils.generateId(username, "-", false, 30), 30);
        if (uwcompatref != null && session.exists(uwcompatref)) {
            return uwcompatref;
        }
        new UnrestrictedUWSCreator(uwref, session, principal, username).create();
        return uwref;
    }

    protected PathRef resolveUserWorkspace(CoreSession session, PathRef rootref, String username, String workspacename, int maxsize) {
        PathRef uwref = new PathRef(rootref, workspacename);
        if (workspacename.length() == maxsize && !new UnrestrictedPermissionChecker(session, uwref).hasPermission()) {
            String substring = workspacename.substring(0, workspacename.length()-8);
            return new PathRef(rootref, substring.concat(digest(username, 8)));
        }
        return uwref;
    }

    @Override
    public DocumentModel getUserPersonalWorkspace(NuxeoPrincipal principal, DocumentModel context) {
        return getCurrentUserPersonalWorkspace(principal, null, context.getCoreSession(), context);
    }

    @Override
    public DocumentModel getUserPersonalWorkspace(String userName, DocumentModel context) {
        UnrestrictedUserWorkspaceFinder finder = new UnrestrictedUserWorkspaceFinder(userName, context);
        finder.runUnrestricted();
        return finder.getDetachedUserWorkspace();
    }

    protected String buildUserWorkspaceTitle(Principal principal, String userName) {
        if (userName == null) {// avoid looking for UserManager for nothing
            return null;
        }
        // get the user service
        UserManager userManager = Framework.getService(UserManager.class);
        if (userManager == null) {
            // for tests
            return userName;
        }

        // Adapter userModel to get its fields (firstname, lastname)
        DocumentModel userModel = userManager.getUserModel(userName);
        if (userModel == null) {
            return userName;
        }

        UserAdapter userAdapter = null;
        userAdapter = userModel.getAdapter(UserAdapter.class);

        if (userAdapter == null) {
            return userName;
        }

        // compute the title
        StringBuilder title = new StringBuilder();
        String firstName = userAdapter.getFirstName();
        if (firstName != null && firstName.trim()
                .length() > 0) {
            title.append(firstName);
        }

        String lastName = userAdapter.getLastName();
        if (lastName != null && lastName.trim()
                .length() > 0) {
            if (title.length() > 0) {
                title.append(" ");
            }
            title.append(lastName);
        }

        if (title.length() > 0) {
            return title.toString();
        }

        return userName;

    }

    protected void notifyEvent(CoreSession coreSession, DocumentModel document, NuxeoPrincipal principal,
            String eventId, Map<String, Serializable> properties) {
        if (properties == null) {
            properties = new HashMap<String, Serializable>();
        }
        EventContext eventContext = null;
        if (document != null) {
            properties.put(CoreEventConstants.REPOSITORY_NAME, document.getRepositoryName());
            properties.put(CoreEventConstants.SESSION_ID, coreSession.getSessionId());
            properties.put(CoreEventConstants.DOC_LIFE_CYCLE, document.getCurrentLifeCycleState());
            eventContext = new DocumentEventContext(coreSession, principal, document);
        } else {
            eventContext = new EventContextImpl(coreSession, principal);
        }
        eventContext.setProperties(properties);
        Event event = eventContext.newEvent(eventId);
        Framework.getLocalService(EventProducer.class)
                .fireEvent(event);
    }

    protected class UnrestrictedRootCreator extends UnrestrictedSessionRunner {
        public UnrestrictedRootCreator(PathRef ref, String username, CoreSession session) {
            super(session);
            this.ref = ref;
            this.username = username;
        }
        PathRef ref;
        final String username;
        DocumentModel doc;

        @Override
        public void run() {
            if (session.exists(ref)) {
                doc = session.getDocument(ref);
            } else {
                try {
                    doc = doCreateUserWorkspacesRoot(session, ref);
                } catch (DocumentNotFoundException e) {
                    // domain may have been removed !
                    targetDomainName = null;
                    ref = new PathRef(computePathUserWorkspaceRoot(session, username, null));
                    doc = doCreateUserWorkspacesRoot(session, ref);
                }
            }
            doc.detach(true);
            assert (doc.getPathAsString()
                    .equals(ref.toString()));
        }

        DocumentModel create() {
            synchronized (UnrestrictedRootCreator.class) {
                runUnrestricted();
                return doc;
            }
        }
    }

    protected class UnrestrictedUWSCreator extends UnrestrictedSessionRunner {

        PathRef userWSRef;

        String userName;

        Principal principal;

        DocumentModel uw;

        public UnrestrictedUWSCreator(PathRef userWSRef, CoreSession userCoreSession,
                Principal principal, String userName) {
            super(userCoreSession);
            this.userWSRef = userWSRef;
            this.userName = userName;
            this.principal = principal;
        }

        @Override
        public void run() {
            if (session.exists(userWSRef)) {
                uw = session.getDocument(userWSRef);
            } else {
                uw = doCreateUserWorkspace(session, userWSRef, principal, userName);
            }
            uw.detach(true);
            assert (uw.getPathAsString()
                    .equals(userWSRef.toString()));
        }

        DocumentModel create() {
            synchronized (UnrestrictedSessionRunner.class) {
                runUnrestricted();
                return uw;
            }
        }

    }

    protected class UnrestrictedPermissionChecker extends UnrestrictedSessionRunner {
        protected UnrestrictedPermissionChecker(CoreSession session, PathRef ref) {
            super(session);
            this.ref = ref;
            principal = session.getPrincipal();
        }

        final Principal principal;

        final PathRef ref;

        boolean hasPermission;

        @Override
        public void run() {
            hasPermission = !session.exists(ref) || session.hasPermission(principal, ref, SecurityConstants.EVERYTHING);
        }

        boolean hasPermission() {
            runUnrestricted();
            return hasPermission;
        }
    }

    protected class UnrestrictedUserWorkspaceFinder extends UnrestrictedSessionRunner {

        protected DocumentModel userWorkspace;

        protected String userName;

        protected DocumentModel context;

        protected UnrestrictedUserWorkspaceFinder(String userName, DocumentModel context) {
            super(context.getCoreSession()
                    .getRepositoryName(), userName);
            this.userName = userName;
            this.context = context;
        }

        @Override
        public void run() {
            userWorkspace = getCurrentUserPersonalWorkspace(null, userName, session, context);
            if (userWorkspace != null) {
                userWorkspace.detach(true);
            }
        }

        public DocumentModel getDetachedUserWorkspace() {
            return userWorkspace;
        }
    }

    protected class RootDomainFinder extends UnrestrictedSessionRunner {

        public RootDomainFinder(CoreSession userCoreSession) {
            super(userCoreSession);
        }

        protected String domaineName;

        @Override
        public void run() {

            String targetName = getComponent().getTargetDomainName();
            PathRef ref = new PathRef("/" + targetName);
            if (session.exists(ref)) {
                domaineName = targetName;
                return;
            }
            // configured domain does not exist !!!
            DocumentModelList domains = session.query("select * from Domain order by dc:created");

            if (!domains.isEmpty()) {
                domaineName = domains.get(0)
                        .getName();
            }
        }
    }

    protected UserWorkspaceServiceImplComponent getComponent() {
        return (UserWorkspaceServiceImplComponent) Framework.getRuntime()
                .getComponent(
                        UserWorkspaceServiceImplComponent.NAME);
    }

    protected abstract DocumentModel doCreateUserWorkspacesRoot(CoreSession unrestrictedSession, PathRef rootRef);

    protected abstract DocumentModel doCreateUserWorkspace(CoreSession unrestrictedSession, PathRef wsRef,
            Principal principal, String userName);

}
