package org.airsonic.player.service.search.parser;

public class AdvancedSearchQuerySqlAlbumVisitor extends AdvancedSearchQuerySqlVisitor {

    public AdvancedSearchQuerySqlAlbumVisitor(String username) {
        super(username);
    }

    public static SqlClause toSql(String username, String expr, String orderExpr) throws AdvancedSearchQueryParseError {
        SqlClause clause = AdvancedSearchQuerySqlVisitor.toSql(username, expr, orderExpr);
        clause.andWhere("media_file.type = 'ALBUM'");
        return clause;
    }
}
