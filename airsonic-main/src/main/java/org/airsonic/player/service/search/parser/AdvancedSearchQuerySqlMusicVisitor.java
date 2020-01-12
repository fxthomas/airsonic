package org.airsonic.player.service.search.parser;

public class AdvancedSearchQuerySqlMusicVisitor extends AdvancedSearchQuerySqlVisitor {

    public AdvancedSearchQuerySqlMusicVisitor(String username) {
        super(username);
    }

    public static SqlWhereClause toSql(String username, String expr) {
        SqlWhereClause clause = AdvancedSearchQuerySqlVisitor.toSql(username, expr);
        clause.and("media_file.type = 'MUSIC'");
        return clause;
    }
}
