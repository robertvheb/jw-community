package org.joget.apps.app.controller;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.dao.BuilderDefinitionDao;
import org.joget.apps.app.dao.UserviewDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.UserviewDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.ext.ConsoleWebPlugin;
import org.joget.apps.userview.lib.DefaultTheme;
import org.joget.apps.userview.model.PageComponent;
import org.joget.apps.userview.model.SimplePageComponent;
import org.joget.apps.userview.model.Userview;
import org.joget.apps.userview.model.UserviewBuilderPalette;
import org.joget.apps.userview.model.UserviewCategory;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.apps.userview.model.UserviewPage;
import org.joget.apps.userview.model.UserviewSetting;
import org.joget.apps.userview.model.UserviewTheme;
import org.joget.apps.userview.model.UserviewV5Theme;
import org.joget.apps.userview.service.UserviewService;
import org.joget.apps.userview.service.UserviewThemeProcesser;
import org.joget.apps.userview.service.UserviewUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.HiddenPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.plugin.property.service.PropertyUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserviewBuilderWebController {

    @Autowired
    private UserviewService userviewService;
    @Autowired
    private UserviewBuilderPalette userviewBuilderPalette;
    @Autowired
    AppService appService;
    @Autowired
    UserviewDefinitionDao userviewDefinitionDao;
    @Autowired
    BuilderDefinitionDao builderDefinitionDao;
    @Autowired
    PluginManager pluginManager;

    @RequestMapping("/console/app/(*:appId)/(~:appVersion)/userview/builder/(*:userviewId)")
    public String builder(ModelMap map, HttpServletRequest request, HttpServletResponse response, @RequestParam("appId") String appId, @RequestParam(value = "appVersion", required = false) String appVersion, @RequestParam("userviewId") String userviewId, @RequestParam(required = false) String json) throws Exception {
        // verify app license
        ConsoleWebPlugin consoleWebPlugin = (ConsoleWebPlugin)pluginManager.getPlugin(ConsoleWebPlugin.class.getName());
        String page = consoleWebPlugin.verifyAppVersion(appId, appVersion);
        if (page != null) {
            return page;
        }

        AppDefinition appDef = appService.getAppDefinition(appId, appVersion);
        map.addAttribute("appId", appId);
        map.addAttribute("appVersion", appDef.getVersion());
        map.addAttribute("appDefinition", appDef);

        UserviewDefinition userview = userviewDefinitionDao.loadById(userviewId, appDef);
        if (userview == null) {
            return "error404";
        }
        
        userview = userviewService.combinedUserviewDefinition(userview);
        
        String userviewJson = null;
        if (json != null && !json.trim().isEmpty()) {
            try {
                // validate JSON
                new JSONObject(json);

                // read custom JSON from request
                userviewJson = json;
            } catch (JSONException ex) {
                userviewJson = "{}";
            }
        } else {
            // get JSON from form definition
            userviewJson = userview.getJson();
        }

        map.addAttribute("userviewId", userviewId);
        map.addAttribute("userview", userview);
        map.addAttribute("json", PropertyUtil.propertiesJsonLoadProcessing(userviewJson));

        map.addAttribute("setting", new UserviewSetting());
        map.addAttribute("category", new UserviewCategory());

        Map basicRequestParams = new HashMap();
        basicRequestParams.put("appId", appId);
        basicRequestParams.put("appVersion", appVersion);
        basicRequestParams.put("userviewId", userviewId);
        basicRequestParams.put("contextPath", request.getContextPath());
        
        map.addAttribute("simplePageComponent", getAvailableElements());
        map.addAttribute("menuTypeCategories", userviewBuilderPalette.getUserviewMenuCategoryMap(basicRequestParams));
        
        response.addHeader("X-XSS-Protection", "0");

        return "ubuilder/builder";
    }

    @RequestMapping(value = "/console/app/(*:appId)/(~:appVersion)/userview/builderSave/(*:userviewId)", method = RequestMethod.POST)
    @Transactional
    public String save(Writer writer, @RequestParam("appId") String appId, @RequestParam(value = "appVersion", required = false) String appVersion, @RequestParam("userviewId") String userviewId, @RequestParam("json") String json) throws Exception {
        // verify app version
        ConsoleWebPlugin consoleWebPlugin = (ConsoleWebPlugin)pluginManager.getPlugin(ConsoleWebPlugin.class.getName());
        String page = consoleWebPlugin.verifyAppVersion(appId, appVersion);
        if (page != null) {
            return page;
        }

        JSONObject jsonObject = new JSONObject();

        AppDefinition appDef = appService.getAppDefinition(appId, appVersion);
        UserviewDefinition userview = userviewDefinitionDao.loadById(userviewId, appDef);
        
        String name = StringUtil.stripAllHtmlTag(userviewService.getUserviewName(json));
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        name = StringUtil.unescapeString(name,StringUtil.TYPE_HTML,null);
        userview.setName(name);
        userview.setDescription(userviewService.getUserviewDescription(json));
        
        UserviewDefinition migratedUserview = userviewService.combinedUserviewDefinition(userview);
        
        json = userviewService.saveUserviewPages(PropertyUtil.propertiesJsonStoreProcessing(migratedUserview.getJson(), json), userviewId, appDef);
        userview.setJson(json);

        boolean success = userviewDefinitionDao.update(userview);
        jsonObject.accumulate("success", success);

        jsonObject.write(writer);
        return null;
    }

    @RequestMapping(value = {"/console/app/(*:appId)/(~:appVersion)/userview/builderPreview/(*:userviewId)","/console/app/(*:appId)/(~:appVersion)/userview/builderPreview/(*:userviewId)/(*:menuId)"}, method = RequestMethod.POST)
    public String preview(ModelMap map, HttpServletRequest request, HttpServletResponse response, @RequestParam("appId") String appId, @RequestParam(value = "appVersion", required = false) String appVersion, @RequestParam("userviewId") String userviewId, @RequestParam("json") String json, @RequestParam(value = "menuId", required = false) String menuId) throws Exception {
        // get app definition so that it's set in the current thread
        AppDefinition appDef = appService.getAppDefinition(appId, appVersion);
        map.addAttribute("appId", appId);
        map.addAttribute("appVersion", appDef.getVersion());
        
        String tempJson = json;
        if (tempJson.contains(SecurityUtil.ENVELOPE) || tempJson.contains(PropertyUtil.PASSWORD_PROTECTED_VALUE)) {
            UserviewDefinition userview = userviewDefinitionDao.loadById(userviewId, appDef);
            if (userview != null) {
                userview = userviewService.combinedUserviewDefinition(userview);
                tempJson = PropertyUtil.propertiesJsonStoreProcessing(userview.getJson(), tempJson);
            }
        }

        Userview userviewObject = userviewService.createUserview(tempJson, menuId, true, request.getContextPath(), request.getParameterMap(), null, false);
        UserviewThemeProcesser processer = new UserviewThemeProcesser(userviewObject, request);
        map.addAttribute("userview", userviewObject);
        map.addAttribute("processer", processer);
        map.addAttribute("json", json);
        
        response.addHeader("X-XSS-Protection", "0");
        
        return processer.getPreviewView();
    }
    
    @RequestMapping("/property/userview/json/(*:appId)/(~:version)/getPropertyOptions")
    public void getProperties(Writer writer, @RequestParam("value") String value, @RequestParam(value = "appId", required = true) String appId, @RequestParam(value = "version", required = false) String version) throws Exception {
        if (appId != null && !appId.trim().isEmpty()) {
            appService.getAppDefinition(appId, version);
        }
        
        String propertyOptions = "";
        PropertyEditable element = (PropertyEditable) pluginManager.getPlugin(value);
        if (element != null) {
            propertyOptions = PropertyUtil.injectHelpLink(((Plugin) element).getHelpLink(), element.getPropertyOptions());
            if (element instanceof UserviewTheme) {
                String loginOptions = AppUtil.readPluginResource(DefaultTheme.class.getName(), "/properties/userview/userviewLogin.json", null, true, "message/userview/userviewLogin");
                propertyOptions = UserviewUtil.appendPropertyOptions(propertyOptions, loginOptions);
                if (!(element instanceof UserviewV5Theme)) {
                    String mobileOptions = AppUtil.readPluginResource(DefaultTheme.class.getName(), "/properties/userview/userviewMobile.json", null, true, "message/userview/userviewMobile");
                    propertyOptions = UserviewUtil.appendPropertyOptions(propertyOptions, mobileOptions);
                }
            }
        }
        writer.write(propertyOptions);
    }
    
    @RequestMapping("/ubuilder/app/(*:appId)/(~:appVersion)/(*:userviewId)/page/template")
    public void menuPageTemplate(Writer writer, HttpServletRequest request, HttpServletResponse response, @RequestParam("appId") String appId, @RequestParam(value = "appVersion", required = false) String appVersion, @RequestParam("userviewId") String userviewId, @RequestParam("json") String json) {
        AppDefinition appDef = appService.getAppDefinition(appId, appVersion);

        String tempJson = json;
        if (tempJson.contains(SecurityUtil.ENVELOPE) || tempJson.contains(PropertyUtil.PASSWORD_PROTECTED_VALUE)) {
            UserviewDefinition userview = userviewDefinitionDao.loadById(userviewId, appDef);
            if (userview != null) {
                userview = userviewService.combinedUserviewDefinition(userview);
                tempJson = PropertyUtil.propertiesJsonStoreProcessing(userview.getJson(), tempJson);
            }
        }
        
        tempJson = AppUtil.replaceAppMessages(tempJson, StringUtil.TYPE_JSON);
        tempJson = AppUtil.processHashVariable(tempJson, null, StringUtil.TYPE_JSON, null, appDef);
        
        response.addHeader("X-XSS-Protection", "0");

        try {
            JSONObject jObj = new JSONObject(tempJson);
            UserviewMenu menu = (UserviewMenu) pluginManager.getPlugin(jObj.getString("className"));
            
            if (menu != null) {
                menu.setProperties(PropertyUtil.getProperties(jObj.getJSONObject("properties")));
                
                Map requestParameters = userviewService.convertRequestParamMap(request.getParameterMap());
                requestParameters.put("contextPath", request.getContextPath());
                requestParameters.put("isPreview", "true");
                requestParameters.put("isBuilder", "true");
                requestParameters.put("appId", appDef.getAppId());
                requestParameters.put("appVersion", appDef.getVersion().toString());
                menu.setRequestParameters(requestParameters);
                menu.setUrl("");
                
                Userview userview = new Userview();
                userview.setProperty("id", userviewId);
                menu.setUserview(userview);
                
                if (jObj.has("referencePage")) {
                    menu.setProperty("REFERENCE_PAGE", jObj.getJSONObject("referencePage"));
                }
                
                UserviewPage page = new UserviewPage(menu);
                
                String html = page.render();
                html = html.replaceAll(StringUtil.escapeRegex("???"), StringUtil.escapeRegex("@@"));
                html = pluginManager.processPluginTranslation(html, menu.getClassName(), null);
                html = html.replaceAll(StringUtil.escapeRegex("@@"), StringUtil.escapeRegex("???"));

                writer.write(html);
            }
        } catch (Exception e) {
            LogUtil.error(UserviewBuilderWebController.class.getName(), e, "");
        }
    }
    
    @RequestMapping("/ubuilder/app/(*:appId)/(~:appVersion)/(*:userviewId)/menu/template")
    public void menuTemplate(Writer writer, HttpServletRequest request, HttpServletResponse response, @RequestParam("appId") String appId, @RequestParam(value = "appVersion", required = false) String appVersion, @RequestParam("userviewId") String userviewId, @RequestParam("json") String json) {
        AppDefinition appDef = appService.getAppDefinition(appId, appVersion);

        String tempJson = json;
        if (tempJson.contains(SecurityUtil.ENVELOPE) || tempJson.contains(PropertyUtil.PASSWORD_PROTECTED_VALUE)) {
            UserviewDefinition userview = userviewDefinitionDao.loadById(userviewId, appDef);
            if (userview != null) {
                userview = userviewService.combinedUserviewDefinition(userview);
                tempJson = PropertyUtil.propertiesJsonStoreProcessing(userview.getJson(), tempJson);
            }
        }
        
        tempJson = AppUtil.replaceAppMessages(tempJson, StringUtil.TYPE_JSON);
        tempJson = AppUtil.processHashVariable(tempJson, null, StringUtil.TYPE_JSON, null, appDef);
        
        response.addHeader("X-XSS-Protection", "0");

        try {
            JSONObject jObj = new JSONObject(tempJson);
            UserviewMenu menu = (UserviewMenu) pluginManager.getPlugin(jObj.getString("className"));
            
            if (menu != null) {
                menu.setProperties(PropertyUtil.getProperties(jObj.getJSONObject("properties")));
                
                Map requestParameters = userviewService.convertRequestParamMap(request.getParameterMap());
                requestParameters.put("contextPath", request.getContextPath());
                requestParameters.put("isPreview", "true");
                requestParameters.put("isBuilder", "true");
                requestParameters.put("appId", appDef.getAppId());
                requestParameters.put("appVersion", appDef.getVersion().toString());
                menu.setRequestParameters(requestParameters);
                menu.setUrl("");
                
                Userview userview = new Userview();
                userview.setProperty("id", userviewId);
                menu.setUserview(userview);
                
                writer.write(menu.getMenu());
            }
        } catch (Exception e) {
            LogUtil.error(UserviewBuilderWebController.class.getName(), e, "");
        }
    }
    
    @RequestMapping("/ubuilder/app/(*:appId)/(~:appVersion)/(*:userviewId)/component/template")
    public void componentTemplate(Writer writer, HttpServletRequest request, HttpServletResponse response, @RequestParam("appId") String appId, @RequestParam(value = "appVersion", required = false) String appVersion, @RequestParam("userviewId") String userviewId, @RequestParam("json") String json) {
        AppDefinition appDef = appService.getAppDefinition(appId, appVersion);

        String tempJson = json;
        if (tempJson.contains(SecurityUtil.ENVELOPE) || tempJson.contains(PropertyUtil.PASSWORD_PROTECTED_VALUE)) {
            UserviewDefinition userview = userviewDefinitionDao.loadById(userviewId, appDef);
            if (userview != null) {
                userview = userviewService.combinedUserviewDefinition(userview);
                tempJson = PropertyUtil.propertiesJsonStoreProcessing(userview.getJson(), tempJson);
            }
        }
        
        tempJson = AppUtil.replaceAppMessages(tempJson, StringUtil.TYPE_JSON);
        tempJson = AppUtil.processHashVariable(tempJson, null, StringUtil.TYPE_JSON, null, appDef);
        
        response.addHeader("X-XSS-Protection", "0");

        try {
            JSONObject jObj = new JSONObject(tempJson);
            PageComponent pc = (PageComponent) pluginManager.getPlugin(jObj.getString("className"));
            
            if (pc != null) {
                pc.setProperties(PropertyUtil.getProperties(jObj.getJSONObject("properties")));
            
                Map requestParameters = userviewService.convertRequestParamMap(request.getParameterMap());
                requestParameters.put("contextPath", request.getContextPath());
                requestParameters.put("isPreview", "true");
                requestParameters.put("isBuilder", "true");
                requestParameters.put("appId", appDef.getAppId());
                requestParameters.put("appVersion", appDef.getVersion().toString());
                pc.setRequestParameters(requestParameters);
                
                if (pc instanceof UserviewMenu) {
                    ((UserviewMenu) pc).setUrl("");
                    Userview userview = new Userview();
                    userview.setProperty("id", userviewId);
                    ((UserviewMenu) pc).setUserview(userview);
                }
                
                String html = pc.render();
                html = html.replaceAll(StringUtil.escapeRegex("???"), StringUtil.escapeRegex("@@"));
                html = pluginManager.processPluginTranslation(html, pc.getClassName(), null);
                html = html.replaceAll(StringUtil.escapeRegex("@@"), StringUtil.escapeRegex("???"));

                writer.write(html);
            }
        } catch (Exception e) {
            LogUtil.error(UserviewBuilderWebController.class.getName(), e, "");
        }
    }
    
    @RequestMapping({"/json/console/app/(*:appId)/(~:version)/userview/builder/(*:userviewId)/json"})
    public void getUserviewJson(Writer writer, HttpServletResponse response, @RequestParam(value = "appId") String appId, @RequestParam(value = "version", required = false) String version, @RequestParam(value = "userviewId") String userviewId) throws IOException {
        AppDefinition appDef = appService.getAppDefinition(appId, version);
        if (appDef == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        UserviewDefinition userview = userviewDefinitionDao.loadById(userviewId, appDef);
        userview = userviewService.combinedUserviewDefinition(userview);
        if (userview == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String userviewJson = userview.getJson();
        writer.write(PropertyUtil.propertiesJsonLoadProcessing(userviewJson));
    }
    
    protected Collection<SimplePageComponent> getAvailableElements() {
        Collection<SimplePageComponent> list = new ArrayList<SimplePageComponent>();
        Collection<Plugin> pluginList = pluginManager.list(SimplePageComponent.class);
        for (Plugin plugin : pluginList) {
            if (plugin instanceof SimplePageComponent && !(plugin instanceof HiddenPlugin)) {
                list.add((SimplePageComponent) plugin);
            }
        }
        return list;
    }
}