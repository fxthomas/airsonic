/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.command.SearchCommand;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.search.parser.AdvancedSearchQuerySqlVisitor;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * Controller for the search page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/advsearch")
public class AdvancedSearchController {

    private static final int MATCH_COUNT = 0;

    @Autowired
    private SecurityService securityService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private MediaFileDao mediaFileDao;

    @GetMapping
    protected String displayForm() {
        return "advsearch";
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model) {
        model.addAttribute("command",new SearchCommand());
    }

    private static final Logger LOG = LoggerFactory.getLogger(SearchController.class);

    @PostMapping
    protected String onSubmit(HttpServletRequest request, HttpServletResponse response,@ModelAttribute("command") SearchCommand command, Model model)
            throws Exception {

        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = settingsService.getUserSettings(user.getUsername());
        command.setUser(user);
        command.setPartyModeEnabled(userSettings.isPartyModeEnabled());

        List<MusicFolder> musicFolders = settingsService.getMusicFoldersForUser(user.getUsername());
        String query = StringUtils.trimToNull(command.getQuery());
        String orderBy = StringUtils.trimToNull(command.getOrderBy());
        int limit = command.getLimit() > 0 ? command.getLimit() : MATCH_COUNT;

        if (query != null) {
            command.setQuery(query);
            try {
                command.setSongs(mediaFileDao.searchAdvancedSongs(user.getUsername(), query, limit, orderBy));
                // command.setAlbums(mediaFileDao.searchAdvancedAlbums(user.getUsername(), query, limit, orderBy));
                // command.setArtists(mediaFileDao.searchAdvancedArtists(user.getUsername(), query, limit, orderBy));
            } catch (AdvancedSearchQuerySqlVisitor.AdvancedSearchQueryParseError e) {
                command.setErrorMessage(e.getMessage());
            }
            command.setPlayer(playerService.getPlayer(request, response));
        }

        return "advsearch";
    }

}
