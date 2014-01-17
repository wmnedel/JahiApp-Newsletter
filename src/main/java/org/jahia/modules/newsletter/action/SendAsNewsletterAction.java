/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */

package org.jahia.modules.newsletter.action;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.bin.Render;
import org.jahia.modules.newsletter.service.NewsletterService;
import org.jahia.params.valves.TokenAuthValveImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.rules.BackgroundAction;
import org.jahia.services.notification.HttpClientService;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An action and a background task that sends the content of the specified node as a newsletter
 * to its subscribers.
 * 
 * @author Thomas Draier
 * @author Sergiy Shyrkov
 */
public class SendAsNewsletterAction extends Action implements BackgroundAction {

    private static final Logger logger = LoggerFactory.getLogger(SendAsNewsletterAction.class);

    @Autowired
    private transient HttpClientService httpClientService;
    @Autowired
    private transient NewsletterService newsletterService;
    private String localServerURL;

    public ActionResult doExecute(final HttpServletRequest req, final RenderContext renderContext,
                                  Resource resource, JCRSessionWrapper session, Map<String, List<String>> parameters, URLResolver urlResolver)
            throws Exception {
        JCRNodeWrapper node = resource.getNode();
        Map<String, String> newsletterVersions = new HashMap<String, String>();

        boolean newsletterSent = newsletterService.sendIssueToSubscribers(node, renderContext, newsletterVersions);

        if(newsletterSent){
            return ActionResult.OK;
        }else {
            return ActionResult.INTERNAL_ERROR;
        }
    }

    public void executeBackgroundAction(JCRNodeWrapper node) {
        // do local post on node.getPath/sendAsNewsletter.do
        try {
            Map<String,String> headers = new HashMap<String,String>();
            headers.put("jahiatoken",TokenAuthValveImpl.addToken(node.getSession().getUser()));
            headers.put("accept", "text/plain");
            String out = httpClientService.executePost(localServerURL + Render.getRenderServletPath() + "/live/"
                            + node.getResolveSite().getDefaultLanguage() + node.getPath()
                            + ".sendAsNewsletter.do", null, headers);
            logger.info(out);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public HttpClientService getHttpClientService() {
        return httpClientService;
    }

    public void setHttpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    public NewsletterService getNewsletterService() {
        return newsletterService;
    }

    public void setNewsletterService(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    public String getLocalServerURL() {
        return localServerURL;
    }

    public void setLocalServerURL(String localServerURL) {
        this.localServerURL = localServerURL;
    }
}