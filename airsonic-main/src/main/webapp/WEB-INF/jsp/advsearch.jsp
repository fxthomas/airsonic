<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>
<%--@elvariable id="command" type="org.airsonic.player.command.SearchCommand"--%>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>

    <script type="text/javascript" src="<c:url value='/script/DataTables/datatables.min.js'/>"></script>
    <link rel="stylesheet" href="<c:url value='/script/DataTables/datatables.min.css'/>" type="text/css">

    <script type="text/javascript" language="javascript">
        $(function() {
            $("#artists-results").DataTables();
            $("#albums-results").DataTables();
            $("#songs-results").DataTables();
        });
    </script>
</head>
<body class="mainframe bgcolor1">

<h1>
    <img src="<spring:theme code='searchImage'/>" alt=""/>
    <span style="vertical-align: middle"><fmt:message key="search.title"/></span>
</h1>

<c:if test="${command.indexBeingCreated}">
    <p class="warning"><fmt:message key="search.index"/></p>
</c:if>

<form method="post" action="advsearch.view" target="main" name="searchForm">
    <td><input required type="text" name="query" id="query" size="28" placeholder="${search}" value="${command.query}"></td>
    <td><input type="submit" value="Search"></td>
</form>

<c:if test="${not command.indexBeingCreated and empty command.artists and empty command.albums and empty command.songs}">
    <p class="warning"><fmt:message key="search.hits.none"/></p>
</c:if>

<c:if test="${not empty command.artists}">
    <h2><b><fmt:message key="search.hits.artists"/></b></h2>
    <table id="artists-results" class="music indent">
        <c:forEach items="${command.artists}" var="match" varStatus="loopStatus">

            <sub:url value="/main.view" var="mainUrl">
                <sub:param name="path" value="${match.path}"/>
            </sub:url>

            <tr class="artistRow">
                <td class="truncate"><a href="${mainUrl}">${fn:escapeXml(match.name)}</a></td>
            </tr>

        </c:forEach>
    </table>
</c:if>

<c:if test="${not empty command.albums}">
    <h2><b><fmt:message key="search.hits.albums"/></b></h2>
    <table id="albums-results" class="music indent">
        <c:forEach items="${command.albums}" var="match" varStatus="loopStatus">

            <sub:url value="/main.view" var="mainUrl">
                <sub:param name="path" value="${match.path}"/>
            </sub:url>

            <tr class="albumRow">
                <td class="truncate"><a href="${mainUrl}">${fn:escapeXml(match.albumName)}</a></td>
                <td class="truncate"><span class="detail">${fn:escapeXml(match.artist)}</span></td>
            </tr>

        </c:forEach>
    </table>
</c:if>

<c:if test="${not empty command.songs}">
    <h2><b><fmt:message key="search.hits.songs"/></b></h2>
    <table id="songs-results" class="music indent">
        <c:forEach items="${command.songs}" var="match" varStatus="loopStatus">

            <sub:url value="/main.view" var="mainUrl">
                <sub:param name="path" value="${match.parentPath}"/>
            </sub:url>

            <tr class="songRow">
                <td class="truncate"><span class="songTitle">${fn:escapeXml(match.title)}</span></td>
                <td class="truncate"><a href="${mainUrl}"><span class="detail">${fn:escapeXml(match.albumName)}</span></a></td>
                <td class="truncate"><span class="detail">${fn:escapeXml(match.artist)}</span></td>
            </tr>

        </c:forEach>
    </table>
</c:if>

</body></html>
