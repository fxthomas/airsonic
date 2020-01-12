package org.airsonic.player.service.search.parser;

public class AdvancedSearchQuerySqlArtistVisitor extends AdvancedSearchQuerySqlVisitor {

    public AdvancedSearchQuerySqlArtistVisitor(String username) {
        super(username);
    }

    public static SqlWhereClause toSql(String username, String expr) {
        SqlWhereClause clause = AdvancedSearchQuerySqlVisitor.toSql(username, expr);
        clause.and("media_file.type = 'DIRECTORY'");
        return clause;
    }
}
