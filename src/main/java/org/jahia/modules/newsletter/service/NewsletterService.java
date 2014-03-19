/**
 * ==========================================================================================
 * =                        DIGITAL FACTORY v7.0 - Community Distribution                   =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia's Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to "the Tunnel effect", the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 *
 * JAHIA'S DUAL LICENSING IMPORTANT INFORMATION
 * ============================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==========================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, and it is also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ==========================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.newsletter.service;


import org.apache.commons.lang.StringUtils;
import org.jahia.exceptions.JahiaException;
import org.jahia.modules.newsletter.action.UnsubscribeAction;
import org.jahia.modules.newsletter.service.model.Subscription;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.mail.MailService;
import org.jahia.services.notification.HtmlExternalizationService;
import org.jahia.services.notification.HttpClientService;
import org.jahia.services.preferences.user.UserPreferencesHelper;
import org.jahia.services.render.*;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.sites.JahiaSitesService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.PaginatedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: kevan
 * Date: 18/11/13
 * Time: 17:16
 * To change this template use File | Settings | File Templates.
 */
public class NewsletterService {
    private static Logger logger = LoggerFactory.getLogger(NewsletterService.class);
    private static final int READ_CHUNK_SIZE = 1000;
    private static final String JNT_NEWSLETTER = "jnt:newsletter";
    private static final String JNT_NEWSLETTERS = "jnt:newsletters";
    private static final String JNT_NEWSLETTER_ISSUE = "jnt:newsletterIssue";
    private static final String J_LAST_SENT = "j:lastSent";
    private static final String J_SCHEDULED = "j:scheduled";
    private static final String J_PERSONALIZED = "j:personalized";
    private static final String J_PREFERRED_LANGUAGE = "j:preferredLanguage";
    private static final String DEFAULT_USER = "guest";

    @Autowired
    private transient SubscriptionService subscriptionService;
    @Autowired
    private transient HtmlExternalizationService htmlExternalizationService;
    @Autowired
    private transient HttpClientService httpClientService;
    @Autowired
    private transient MailService mailService;
    @Autowired
    private transient RenderService renderService;
    @Autowired
    private transient JahiaSitesService siteService;
    @Autowired
    private transient JahiaUserManagerService userService;

    public boolean sendIssueToSubscribers(final JCRNodeWrapper node, final RenderContext renderContext,
                                          final Map<String, String> newsletterVersions) throws Exception {

        logger.info("Sending content of the node {} as a newsletter", node.getPath());
        long timer = System.currentTimeMillis();

        final boolean personalized = node.hasProperty(J_PERSONALIZED) && node.getProperty(J_PERSONALIZED).getBoolean();

        JCRTemplate.getInstance().doExecuteWithSystemSession(null, "live", new JCRCallback<Boolean>() {
            public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                boolean saveSession = false;
                PaginatedList<Subscription> l = null;
                int total = 0;
                int offset = 0;
                JCRNodeWrapper target = node.getParent();
                do {
                    l = subscriptionService.getSubscriptions(target.getIdentifier(), true, true, null, false, offset, READ_CHUNK_SIZE, session);
                    total = l.getTotalSize();
                    for (Subscription subscription : l.getData()) {
                        if (StringUtils.isEmpty(subscription.getEmail())) {
                            logger.warn("Empty e-mail found for the subscription {}. Skipping.", subscription.getSubscriber());
                            continue;
                        }

                        String username = DEFAULT_USER;

                        JahiaSite site = null;
                        try {
                            site = siteService.getSiteByKey(node.getResolveSite().getSiteKey());
                        } catch (JahiaException ignored) {
                        }

                        if (personalized && subscription.isRegisteredUser() && subscription.getSubscriber() != null) {
                            username = subscription.getSubscriber();
                        }

                        JahiaUser user = subscription.isRegisteredUser() ? userService.lookupUserByKey(subscription.getSubscriber()) : userService.lookupUser(DEFAULT_USER);
                        RenderContext letterContext = new RenderContext(renderContext.getRequest(), renderContext.getResponse(), user);
                        letterContext.setEditMode(renderContext.isEditMode());
                        letterContext.setServletPath(renderContext.getServletPath());
                        letterContext.setWorkspace(renderContext.getWorkspace());
                        Locale language = subscription.isRegisteredUser() ?
                                UserPreferencesHelper.getPreferredLocale(user, site) :
                                LanguageCodeConverters.languageCodeToLocale(subscription.getProperties().get(J_PREFERRED_LANGUAGE));
                        if (language == null) {
                            language = LanguageCodeConverters.languageCodeToLocale(site != null ? site.getDefaultLanguage() : SettingsBean.getInstance().getDefaultLanguageCode());
                        }
                        String confirmationKey = subscription.getConfirmationKey();
                        if (confirmationKey == null) {
                            try {
                                JCRNodeWrapper subscriptionNode = session.getNodeByUUID(subscription.getId());
                                confirmationKey = subscriptionService.generateConfirmationKey(subscriptionNode);
                                letterContext.getRequest().setAttribute("org.jahia.modules.newsletter.unsubscribeLink", UnsubscribeAction.generateUnsubscribeLink(target, confirmationKey, renderContext.getRequest()));
                                subscriptionNode.setProperty(SubscriptionService.J_CONFIRMATION_KEY, confirmationKey);
                                saveSession = true;
                            } catch (RepositoryException e) {
                                logger.warn(
                                        "Unable to store the confirmation key for the subscription "
                                                + subscription.getSubscriber(), e);
                            }
                        } else {
                            letterContext.getRequest().setAttribute("org.jahia.modules.newsletter.unsubscribeLink", UnsubscribeAction.generateUnsubscribeLink(target, confirmationKey, renderContext.getRequest()));
                        }
                        sendIssue(letterContext, node, subscription.getEmail(), username, "html",
                                language, "live",
                                newsletterVersions);
                    }

                    offset += READ_CHUNK_SIZE;
                } while (offset < total);

                if (saveSession) {
                    session.save();
                }

                return Boolean.TRUE;
            }
        });

        node.checkout();
        node.setProperty(J_SCHEDULED, (Value) null);
        node.setProperty(J_LAST_SENT, Calendar.getInstance());
        node.getSession().save();

        logger.info("The content of the node {} was sent as a newsletter in {} ms", node.getPath(),
                System.currentTimeMillis() - timer);

        return true;
    }

    public boolean sendIssue(final RenderContext renderContext, final JCRNodeWrapper node, final String email,
                                   final String user, final String type, final Locale locale, final String workspace, final Map<String, String> newsletterVersions)
            throws RepositoryException {

        final String id = node.getIdentifier();

        final String key = locale + user + type;
        if (!newsletterVersions.containsKey(key)) {
            JCRTemplate.getInstance().doExecuteWithUserSession(user, workspace, locale, new JCRCallback<String>() {
                public String doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    boolean isEdit = renderContext.isEditMode();
                    String previousWorkspace = renderContext.getWorkspace();
                    Resource previousResource = renderContext.getMainResource();
                    JCRSiteNode previousSite = renderContext.getSite();
                    SiteInfo previousSiteInfo = renderContext.getSiteInfo();
                    String previousServletPath = renderContext.getServletPath();
                    HashMap<String, Object> removedAttributes = new HashMap<String, Object>();

                    try {
                        renderContext.setEditMode(false);
                        renderContext.setWorkspace(workspace);
                        renderContext.setServletPath("/cms/render");
                        JCRNodeWrapper node = session.getNodeByIdentifier(id);
                        Resource resource = new Resource(node, "html", null, "page");
                        renderContext.setMainResource(resource);
                        renderContext.setSite(node.getResolveSite());
                        renderContext.setSiteInfo(new SiteInfo(node.getResolveSite()));

                        // Clear attributes
                        @SuppressWarnings("rawtypes")
                        Enumeration attributeNames = renderContext.getRequest().getAttributeNames();
                        while (attributeNames.hasMoreElements()) {
                            String attr = (String) attributeNames.nextElement();
                            if (!attr.startsWith("org.jahia.modules.newsletter.")) {
                                removedAttributes.put(attr, renderContext.getRequest().getAttribute(attr));
                                renderContext.getRequest().removeAttribute(attr);
                            }
                        }

                        String out = renderService.render(resource, renderContext);
                        out = htmlExternalizationService.externalize(out, renderContext);
                        newsletterVersions.put(key, out);

                        String title = node.getName();
                        if (node.hasProperty("jcr:title")) {
                            title = node.getProperty("jcr:title").getString();
                        }
                        newsletterVersions.put(key + ".title", title);
                    } catch (RenderException e) {
                        throw new RepositoryException(e);
                    } finally {
                        renderContext.setEditMode(isEdit);
                        renderContext.setWorkspace(previousWorkspace);
                        renderContext.setMainResource(previousResource);
                        renderContext.setSite(previousSite);
                        renderContext.setSiteInfo(previousSiteInfo);
                        renderContext.setServletPath(previousServletPath);
                        for (String key : removedAttributes.keySet()){
                            renderContext.getRequest().setAttribute(key, removedAttributes.get(key));
                        }
                    }
                    return null;
                }
            });
        }
        String out = newsletterVersions.get(key);
        String subject = newsletterVersions.get(key + ".title");
        if (logger.isDebugEnabled()) {
            logger.debug("Send newsltter to " + email + " , subject " + subject);
            logger.debug(out);
        }
        return mailService.sendHtmlMessage(mailService.defaultSender(), email, null, null, subject, out);
    }

    public List<JCRNodeWrapper> getSiteNewsletters(JCRSiteNode site, String orderBy, boolean orderAscending, JCRSessionWrapper session){
        long timer = System.currentTimeMillis();

        final List<JCRNodeWrapper> newsletters = new LinkedList<JCRNodeWrapper>();
        try {
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            if (queryManager == null) {
                logger.error("Unable to obtain QueryManager instance");
                return newsletters;
            }

            StringBuilder q = new StringBuilder();
            q.append("select * from [" + JNT_NEWSLETTER + "] where isdescendantnode([").append(site.getPath())
                    .append("])");
            if (orderBy != null) {
                q.append(" order by [").append(orderBy).append("]").append(orderAscending ? "asc" : "desc");
            }
            Query query = queryManager.createQuery(q.toString(), Query.JCR_SQL2);

            for (NodeIterator nodes = query.execute().getNodes(); nodes.hasNext();) {
                newsletters.add((JCRNodeWrapper) nodes.next());
            }
        } catch (RepositoryException e) {
            logger.error("Error retrieving newsletters for site " + site.getDisplayableName(), e);
        }

        if (logger.isDebugEnabled()) {
            logger.info("Site newsletters search took " + (System.currentTimeMillis() - timer) + " ms. Returning " +
                    newsletters.size() + " newsletter(s)");
        }

        return newsletters;
    }

    public List<JCRNodeWrapper> getNewsletterIssues(String targetNewsletter, String orderBy, boolean orderAscending, JCRSessionWrapper session){
        long timer = System.currentTimeMillis();

        final List<JCRNodeWrapper> issues = new LinkedList<JCRNodeWrapper>();
        try {
            JCRNodeWrapper target = session.getNodeByIdentifier(targetNewsletter);
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            if (queryManager == null) {
                logger.error("Unable to obtain QueryManager instance");
                return issues;
            }

            StringBuilder q = new StringBuilder();
            q.append("select * from [" + JNT_NEWSLETTER_ISSUE + "] where isdescendantnode([").append(target.getPath())
                    .append("])");
            if (orderBy != null) {
                q.append(" order by [").append(orderBy).append("]").append(orderAscending ? "asc" : "desc");
            }
            Query query = queryManager.createQuery(q.toString(), Query.JCR_SQL2);

            for (NodeIterator nodes = query.execute().getNodes(); nodes.hasNext();) {
                issues.add((JCRNodeWrapper) nodes.next());
            }
        } catch (RepositoryException e) {
            logger.error("Error retrieving newsletter issues for node " + targetNewsletter, e);
        }

        if (logger.isDebugEnabled()) {
            logger.info("Newsletter issues search took " + (System.currentTimeMillis() - timer) + " ms. Returning " +
                    issues.size() + " issue(s)");
        }

        return issues;
    }

    public JCRNodeWrapper getNewslettersRootNode(JCRSiteNode site, JCRSessionWrapper session) {
        StringBuilder q = new StringBuilder();
        q.append("select * from [" + JNT_NEWSLETTERS + "] where isdescendantnode([").append(site.getPath())
                .append("])");
        try {
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            if (queryManager == null) {
                logger.error("Unable to obtain QueryManager instance");
                return null;
            }
            Query query = queryManager.createQuery(q.toString(), Query.JCR_SQL2);
            query.setLimit(1);
            final NodeIterator nodeIterator = query.execute().getNodes();
            if (nodeIterator.hasNext()) {
                return (JCRNodeWrapper) nodeIterator.nextNode();
            }
        } catch (RepositoryException e) {
            logger.error("Error retrieving newsletters root node for site " + site.getDisplayableName(), e);
        }
        return null;
    }


    public SubscriptionService getSubscriptionService() {
        return subscriptionService;
    }

    public void setSubscriptionService(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public HtmlExternalizationService getHtmlExternalizationService() {
        return htmlExternalizationService;
    }

    public void setHtmlExternalizationService(HtmlExternalizationService htmlExternalizationService) {
        this.htmlExternalizationService = htmlExternalizationService;
    }

    public HttpClientService getHttpClientService() {
        return httpClientService;
    }

    public void setHttpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    public MailService getMailService() {
        return mailService;
    }

    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    public RenderService getRenderService() {
        return renderService;
    }

    public void setRenderService(RenderService renderService) {
        this.renderService = renderService;
    }

    public JahiaSitesService getSiteService() {
        return siteService;
    }

    public void setSiteService(JahiaSitesService siteService) {
        this.siteService = siteService;
    }

    public JahiaUserManagerService getUserService() {
        return userService;
    }

    public void setUserService(JahiaUserManagerService userService) {
        this.userService = userService;
    }
}
