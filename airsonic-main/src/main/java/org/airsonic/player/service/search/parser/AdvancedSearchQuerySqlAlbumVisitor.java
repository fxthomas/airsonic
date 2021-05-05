package org.airsonic.player.service.search.parser;

public class AdvancedSearchQuerySqlAlbumVisitor extends AdvancedSearchQuerySqlVisitor {

    public AdvancedSearchQuerySqlAlbumVisitor(String username) {
        super(username);
    }

    public static SqlWhereClause toSql(String username, String expr) throws AdvancedSearchQueryParseError {
        SqlWhereClause clause = AdvancedSearchQuerySqlVisitor.toSql(username, expr);
        clause.and("media_file.type = 'ALBUM'");
        return clause;
    }
}
