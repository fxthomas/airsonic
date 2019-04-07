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

import chameleon.playlist.*;
import org.airsonic.player.dao.InternetRadioDao;
import org.airsonic.player.domain.InternetRadio;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the playlist frame.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/radioQueue")
public class RadioQueueController {

    @Autowired
    private PlayerService playerService;
    @Autowired
    private InternetRadioDao internetRadioDao;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private SettingsService settingsService;

    private static final Logger LOG = LoggerFactory.getLogger(RadioQueueController.class);

    @RequestMapping(method = RequestMethod.GET)
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {

        int id = ServletRequestUtils.getIntParameter(request, "id");
        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = settingsService.getUserSettings(user.getUsername());
        Player player = playerService.getPlayer(request, response);

        // Load URIs from the stream URLs
        InternetRadio radio = null;
        for (InternetRadio currentRadio : internetRadioDao.getAllInternetRadios()) {
            if (currentRadio.isEnabled() && currentRadio.getId() == id) {
                radio = currentRadio;
                break;
            }
        }
        if (radio == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Internet radio not found: " + id);
            return null;
        }

        // Retrieve radio playlist and parse it
        URL playlistUrl = new URL(radio.getStreamUrl());
        LOG.info("Parsing playlist at {}...", playlistUrl.toString());
        SpecificPlaylist inputPlaylist = SpecificPlaylistFactory.getInstance().readFrom(playlistUrl);
        if (inputPlaylist == null) {
            throw new Exception("Unsupported playlist format " + playlistUrl.toString());
        }

        // Retrieve stream URLs
        List<String> radioStreamUrls = new ArrayList<>();
        inputPlaylist.toPlaylist().acceptDown(new PlaylistVisitor() {
            @Override
            public void beginVisitPlaylist(Playlist playlist) throws Exception {

            }

            @Override
            public void endVisitPlaylist(Playlist playlist) throws Exception {

            }

            @Override
            public void beginVisitParallel(Parallel parallel) throws Exception {

            }

            @Override
            public void endVisitParallel(Parallel parallel) throws Exception {

            }

            @Override
            public void beginVisitSequence(Sequence sequence) throws Exception {

            }

            @Override
            public void endVisitSequence(Sequence sequence) throws Exception {

            }

            @Override
            public void beginVisitMedia(Media media) throws Exception {
                radioStreamUrls.add(media.getSource().getURI().toString());
                LOG.info("Got source media at {}...", media.getSource().getURI().toString());
            }

            @Override
            public void endVisitMedia(Media media) throws Exception {

            }
        });

        Map<String, Object> map = new HashMap<>();
        map.put("user", user);
        map.put("player", player);
        map.put("players", playerService.getPlayersForUserAndClientId(user.getUsername(), null));
        map.put("visibility", userSettings.getPlaylistVisibility());
        map.put("partyMode", userSettings.isPartyModeEnabled());
        map.put("notify", userSettings.isSongNotificationEnabled());
        map.put("autoHide", userSettings.isAutoHidePlayQueue());
        map.put("radioStreamUrls", radioStreamUrls);
        map.put("radioName", radio.getName());
        map.put("radioHomepageUrl", radio.getHomepageUrl());
        return new ModelAndView("radioQueue", "model", map);
    }
}
