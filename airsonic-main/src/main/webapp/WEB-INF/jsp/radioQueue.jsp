<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script type="text/javascript" src="<c:url value="/script/scripts-2.0.js"/>"></script>
    <script type="text/javascript" src="<c:url value="/script/mediaelement/mediaelement-and-player.min.js"/>"></script>
    <%@ include file="playQueueCast.jsp" %>
    <style type="text/css">
        .ui-slider .ui-slider-handle {
            width: 11px;
            height: 11px;
            cursor: pointer;
        }
        .ui-slider a {
            outline:none;
        }
        .ui-slider {
            cursor: pointer;
        }
    </style>
</head>

<body class="bgcolor2 playlistframe" onload="init()">

<span id="dummy-animation-target" style="max-width: ${model.autoHide ? 50 : 150}px; display: none"></span>

<script type="text/javascript" language="javascript">
    var isVisible = ${model.autoHide ? 'false' : 'true'};
    var CastPlayer = new CastPlayer();

    function init() {
        <c:if test="${model.autoHide}">initAutoHide();</c:if>
        createPlayer();
        onStart();
    }

    function onHidePlayQueue() {
      setFrameHeight(50);
      isVisible = false;
    }

    function onShowPlayQueue() {
      var height = $("body").height() + 25;
      height = Math.min(height, window.top.innerHeight * 0.8);
      setFrameHeight(height);
      isVisible = true;
    }

    function onTogglePlayQueue() {
      if (isVisible) onHidePlayQueue();
      else onShowPlayQueue();
    }

    function initAutoHide() {
        $(window).mouseleave(function (event) {
            if (event.clientY < 30) onHidePlayQueue();
        });

        $(window).mouseenter(function () {
            onShowPlayQueue();
        });
    }

    function setFrameHeight(height) {
        <%-- Disable animation in Chrome. It stopped working in Chrome 44. --%>
        var duration = navigator.userAgent.indexOf("Chrome") != -1 ? 0 : 400;

        $("#dummy-animation-target").stop();
        $("#dummy-animation-target").animate({"max-width": height}, {
            step: function (now, fx) {
                top.document.getElementById("playQueueFrameset").rows = "*," + now;
            },
            duration: duration
        });
    }

    function onEnded() {
        onNext(repeatEnabled);
    }

    function createPlayer() {
        $('#audioPlayer').get(0).addEventListener("ended", onEnded);
    }

    function getPlayQueue() {
        playQueueService.getPlayQueue(playQueueCallback);
    }

    /**
     * Start/resume playing from the current playlist
     */
    function onStart() {
        if (CastPlayer.castSession) {
            CastPlayer.playCast();
        } else if ($('#audioPlayer').get(0)) {
            if ($('#audioPlayer').get(0).src) {
                $('#audioPlayer').get(0).play();  // Resume playing if the player was paused
            }
            else {
                skip(0);  // Start the first track if the player was not yet loaded
            }
        }
    }

    /**
     * Pause playing
     */
    function onStop() {
        if (CastPlayer.castSession) {
            CastPlayer.pauseCast();
        } else if ($('#audioPlayer').get(0)) {
            $('#audioPlayer').get(0).pause();
        }
    }

    /**
     * Toggle play/pause
     */
    function onToggleStartStop() {
        if (CastPlayer.castSession) {
            var playing = CastPlayer.mediaSession && CastPlayer.mediaSession.playerState == chrome.cast.media.PlayerState.PLAYING;
            if (playing) onStop();
            else onStart();
        } else if ($('#audioPlayer').get(0)) {
            var playing = $("#audioPlayer").get(0).paused != null && !$("#audioPlayer").get(0).paused;
            if (playing) onStop();
            else onStart();
        }
    }

    function onGain(gain) {
        playQueueService.setGain(gain);
    }

    function onJukeboxVolumeChanged() {
        var value = parseInt($("#jukeboxVolume").slider("option", "value"));
        onGain(value / 100);
    }

    function onCastVolumeChanged() {
        var value = parseInt($("#castVolume").slider("option", "value"));
        CastPlayer.setCastVolume(value / 100, false);
    }

    /**
     * Increase or decrease volume by a certain amount
     *
     * @param amount to add or remove from the current volume
     */
    function onGainAdd(gain) {
        if (CastPlayer.castSession) {
            var volume = parseInt($("#castVolume").slider("option", "value")) + gain;
            if (volume > 100) volume = 100;
            if (volume < 0) volume = 0;
            CastPlayer.setCastVolume(volume / 100, false);
            $("#castVolume").slider("option", "value", volume); // Need to update UI
        } else if ($('#audioPlayer').get(0)) {
            var volume = parseFloat($('#audioPlayer').get(0).volume)*100 + gain;
            if (volume > 100) volume = 100;
            if (volume < 0) volume = 0;
            $('#audioPlayer').get(0).volume = volume / 100;
        } else {
            var volume = parseInt($("#jukeboxVolume").slider("option", "value")) + gain;
            if (volume > 100) volume = 100;
            if (volume < 0) volume = 0;
            onGain(volume / 100);
            $("#jukeboxVolume").slider("option", "value", volume); // Need to update UI
        }
    }
</script>

<div class="bgcolor2" style="position:fixed; bottom:0; width:100%;padding-top:10px;">
  <table style="white-space:nowrap; margin-bottom:0;">
    <tr style="white-space:nowrap;">
      <td>
        <div id="player" style="width:340px; height:40px;padding-right:10px">
          <audio id="audioPlayer" class="mejs__player" data-mejsoptions='{"alwaysShowControls": true, "enableKeyboard": false}' width="340px" height="40px" tabindex="-1">
            <c:forEach items="${model.radioStreamUrls}" var="url" varStatus="loopStatus">
            <source src="${fn:escapeXml(url)}" title="${fn:escapeXml(url)}">
            </c:forEach>
          </audio>
        </div>
        <div id="castPlayer" style="display: none">
          <div style="float:left">
            <img id="castPlay" src="<spring:theme code="castPlayImage"/>" onclick="CastPlayer.playCast()" style="cursor:pointer">
            <img id="castPause" src="<spring:theme code="castPauseImage"/>" onclick="CastPlayer.pauseCast()" style="cursor:pointer; display:none">
            <img id="castMuteOn" src="<spring:theme code="volumeImage"/>" onclick="CastPlayer.castMuteOn()" style="cursor:pointer">
            <img id="castMuteOff" src="<spring:theme code="muteImage"/>" onclick="CastPlayer.castMuteOff()" style="cursor:pointer; display:none">
          </div>
          <div style="float:left">
            <div id="castVolume" style="width:80px;height:4px;margin-left:10px;margin-right:10px;margin-top:8px"></div>
            <script type="text/javascript">
                    $("#castVolume").slider({max: 100, value: 50, animate: "fast", range: "min"});
                    $("#castVolume").on("slidestop", onCastVolumeChanged);
            </script>
          </div>
        </div>
      </td>
      <td>
        <img id="castOn" src="<spring:theme code="castIdleImage"/>" onclick="CastPlayer.launchCastApp()" style="cursor:pointer; display:none">
        <img id="castOff" src="<spring:theme code="castActiveImage"/>" onclick="CastPlayer.stopCastApp()" style="cursor:pointer; display:none">
      </td>
    </tr>
  </table>
</div>


<h2 style="float:left">Internet Radio</h2>
<h2 id="songCountAndDuration" style="float:right;padding-right:1em"></h2>
<div style="clear:both"></div>
<p id="empty"><em>Playing <a href="${model.radioHomepageUrl}">${model.radioName}</a>...</em></p>

<script type="text/javascript">
    window['__onGCastApiAvailable'] = function(isAvailable) {
        if (isAvailable) {
            CastPlayer.initializeCastPlayer();
        }
    };
</script>
<script type="text/javascript" src="https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1"></script>

</body></html>
