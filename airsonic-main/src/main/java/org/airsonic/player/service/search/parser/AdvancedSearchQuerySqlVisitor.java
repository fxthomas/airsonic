package org.airsonic.player.service.search.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.*;

public class AdvancedSearchQuerySqlVisitor extends org.airsonic.player.service.search.parser.AdvancedSearchQueryBaseVisitor<AdvancedSearchQuerySqlVisitor.SqlClause> {

    protected String username;

    public static class AdvancedSearchQueryParseError extends Exception {
        public AdvancedSearchQueryParseError(String message) {
            super(message);
        }
    }

    public enum Field {

        // Database-backed fields
        PATH("path"),
        TITLE("title"),
        ALBUM("album"),
        ARTIST("artist"),
        ALBUM_ARTIST("album_artist"),
        DISC_NUMBER("discnumber"),
        TRACK_NUMBER("tracknumber"),
        BIT_RATE("bitrate"),
        DURATION("duration"),
        FILE_SIZE("filesize"),
        COMMENT("comment"),
        CREATED("created"),
        CHANGED("changed"),
        LAST_SCANNED("lastscanned"),
        MB_RECORDING_ID("mb_recording_id"),
        MB_RELEASE_ID("mb_release_id"),
        GENRE("genre"),
        YEAR("year"),
        FOLDER("folder"),
        LAST_PLAYED("lastplayed"),
        FORMAT("format"),
        PLAY_COUNT("play_count"),
        ALBUM_RATING("albumrating"),
        STARRED("starred"),

        // Query-backed fields
        _RANKING("ranking"),
        ;

        private String fieldName;

        Field(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return this.fieldName;
        }

        private static final Map<String, Field> lookup = new HashMap<>();
        static {
            for (Field f: Field.values()) {
                lookup.put(f.getFieldName(), f);
            }
        }

        public static Field get(String fieldName) {
            Field field = lookup.get(fieldName);
            if (field == null) {
                throw new RuntimeException("Unexpected field name: " + fieldName);
            }
            return field;
        }

        public String getSqlFullColumn() {
            String tableName = this.getSqlTable();
            if (tableName != null) {
                return String.format("%s.%s", tableName, getSqlColumn());
            } else {
                return getSqlColumn();
            }
        }

        public String getSqlColumn() {
            switch (this) {
                case PATH:
                    return "path";
                case TITLE:
                    return "title";
                case ALBUM:
                    return "album";
                case ARTIST:
                    return "artist";
                case ALBUM_ARTIST:
                    return "album_artist";
                case DISC_NUMBER:
                    return "disc_number";
                case TRACK_NUMBER:
                    return "track_number";
                case BIT_RATE:
                    return "bit_rate";
                case DURATION:
                    return "duration";
                case FILE_SIZE:
                    return "file_size";
                case COMMENT:
                    return "comment";
                case CREATED:
                    return "created";
                case CHANGED:
                    return "changed";
                case LAST_SCANNED:
                    return "last_scanned";
                case MB_RECORDING_ID:
                    return "mb_release_id";
                case MB_RELEASE_ID:
                    return "mb_recording_id";
                case GENRE:
                    return "genre";
                case YEAR:
                    return "year";
                case FOLDER:
                    return "folder";
                case LAST_PLAYED:
                    return "last_played";
                case FORMAT:
                    return "format";
                case PLAY_COUNT:
                    return "play_count";
                case ALBUM_RATING:
                    return "rating";
                case STARRED:
                    return "id";
                case _RANKING:
                    return "_ranking";
                default:
                    throw new RuntimeException("Unexpected field name: " + this.getFieldName());
            }
        }

        public String getSqlTable() {
            switch (this) {
                case PATH:
                case TITLE:
                case ALBUM:
                case ARTIST:
                case ALBUM_ARTIST:
                case DISC_NUMBER:
                case TRACK_NUMBER:
                case BIT_RATE:
                case DURATION:
                case FILE_SIZE:
                case COMMENT:
                case CREATED:
                case CHANGED:
                case LAST_SCANNED:
                case MB_RECORDING_ID:
                case MB_RELEASE_ID:
                case GENRE:
                case YEAR:
                case FOLDER:
                case LAST_PLAYED:
                case FORMAT:
                case PLAY_COUNT:
                    return "media_file";
                case ALBUM_RATING:
                    return "user_rating";
                case STARRED:
                    return "starred_media_file";
                case _RANKING:
                    return null;
                default:
                    throw new RuntimeException("Unexpected field name: " + this.getFieldName());
            }
        }
    }

    /**
     * This is a simple class for holding parts of an SQL query without a lot of abstraction.
     *
     * The query is separated in WHERE, JOIN and ORDER BY clauses (in separate variables),
     * but everything must be handled manually (e.g. a join must be added with addJoin
     * at the same time as a new term is added to the WHERE clause).
     */
    public static class SqlClause {

        private Map<String, String> additionalSelectClause;
        private StringBuilder whereClause;
        private List<Object> whereClauseArguments;
        private Map<String, String> joins;
        private Map<String, Object> joinArguments;
        private List<String> orderClause;

        public SqlClause() {
            this.additionalSelectClause = new TreeMap<>();
            this.whereClause = new StringBuilder();
            this.whereClauseArguments = new ArrayList<>();
            this.joins = new TreeMap<>();
            this.joinArguments = new TreeMap<>();
            this.orderClause = new ArrayList<>();
        }

        public SqlClause(String expr) {
            this.additionalSelectClause = new TreeMap<>();
            this.whereClause = new StringBuilder();
            this.whereClauseArguments = new ArrayList<>();
            this.joins = new TreeMap<>();
            this.joinArguments = new TreeMap<>();
            this.addWhere(expr);
        }

        public SqlClause(String expr, Object arg) {
            this.additionalSelectClause = new TreeMap<>();
            this.whereClause = new StringBuilder();
            this.whereClauseArguments = new ArrayList<>();
            this.joins = new TreeMap<>();
            this.joinArguments = new TreeMap<>();
            this.addWhere(expr, arg);
        }

        public void addWhere(String expr) {
            this.whereClause.append(expr);
        }

        public void addWhere(String expr, Object arg) {
            if (!expr.contains("?")) throw new RuntimeException("Expression " + expr + " must contain at least one argument");
            this.whereClause.append(expr);
            this.whereClauseArguments.add(arg);
        }

        public void addWhere(SqlClause other) {
            this.whereClause.append(other.whereClause);
            this.whereClauseArguments.addAll(other.whereClauseArguments);
            this.addJoin(other);
        }

        public void addJoin(String alias, String expr) {
            if (this.joins.containsKey(alias) && !this.joins.get(alias).equals(expr)) {
                throw new RuntimeException("Already joined: " + expr);
            }
            this.joins.put(alias, expr);
        }

        public void addJoin(String alias, String expr, Object argument) {
            if (!this.joins.containsKey(alias)) this.joinArguments.put(alias, argument);
            this.addJoin(alias, expr);
        }

        public void addJoin(SqlClause other) {
            for (String alias : other.joins.keySet()) {
                if (other.joinArguments.containsKey(alias)) {
                    this.addJoin(alias, other.joins.get(alias), other.joinArguments.get(alias));
                } else {
                    this.addJoin(alias, other.joins.get(alias));
                }
            }
        }

        public void addOrder(String expr) {
            this.orderClause.add(expr);
        }

        public void addOrder(String expr, boolean ascending) {
            this.orderClause.add(String.format("(%s) %s", expr, ascending ? "ASC" : "DESC"));
        }

        public void addOrder(SqlClause other) {
            this.orderClause.addAll(other.orderClause);
            this.addJoin(other);
            this.addAdditionalSelect(other);
        }

        public void addAdditionalSelect(String alias, String expr) {
            if (this.additionalSelectClause.containsKey(alias) && !this.additionalSelectClause.get(alias).equals(expr)) {
                throw new RuntimeException("Already added as selection: " + expr);
            }
            this.additionalSelectClause.put(alias, expr);
        }

        public void addAdditionalSelect(SqlClause other) {
            this.additionalSelectClause.putAll(other.additionalSelectClause);
        }

        public SqlClause andWhere(String expr) {
            if (this.isWhereClauseEmpty()) this.addWhere(expr);
            else this.addWhere(" AND " + expr);
            return this;
        }

        public SqlClause andWhere(String expr, Object arg) {
            this.andWhere(expr);
            this.whereClauseArguments.add(arg);
            return this;
        }

        public SqlClause andWhere(SqlClause other) {
            if (other.isEmpty()) return this;

            if (!other.isWhereClauseEmpty()) {
                if (this.isWhereClauseEmpty()) {
                    this.whereClause.append(other.whereClause);
                } else {
                    StringBuilder newClause = new StringBuilder();
                    newClause.append("(");
                    newClause.append(this.whereClause);
                    newClause.append(") AND (");
                    newClause.append(other.whereClause);
                    newClause.append(")");
                    this.whereClause = newClause;
                }
                this.whereClauseArguments.addAll(other.whereClauseArguments);
            }

            this.addJoin(other);

            return this;
        }

        public SqlClause orWhere(String expr) {
            if (this.isWhereClauseEmpty()) this.addWhere(expr);
            else this.addWhere(" OR " + expr);
            return this;
        }

        public SqlClause orWhere(String expr, Object arg) {
            this.orWhere(expr);
            this.whereClauseArguments.add(arg);
            return this;
        }

        public SqlClause orWhere(SqlClause other) {
            if (other.isEmpty()) return this;

            if (!other.isWhereClauseEmpty()) {
                if (this.isWhereClauseEmpty()) {
                    this.whereClause.append(other.whereClause);
                } else {
                    StringBuilder newClause = new StringBuilder();
                    newClause.append("(");
                    newClause.append(this.whereClause);
                    newClause.append(") OR (");
                    newClause.append(other.whereClause);
                    newClause.append(")");
                    this.whereClause = newClause;
                }
                this.whereClauseArguments.addAll(other.whereClauseArguments);
            }

            this.addJoin(other);

            return this;
        }

        public SqlClause encloseWhere() {
            if (!isWhereClauseEmpty() && !(this.whereClause.charAt(0) == '(') && this.whereClause.charAt(this.whereClause.length() - 1) == ')') {
                StringBuilder newClause = new StringBuilder();
                newClause.append("(");
                newClause.append(this.whereClause);
                newClause.append(")");
                this.whereClause = newClause;
            }
            return this;
        }

        public boolean isAdditionalSelectClauseEmpty() {
            return this.additionalSelectClause.size() == 0;
        }

        public boolean isWhereClauseEmpty() {
            return this.whereClause.length() == 0 && this.whereClauseArguments.size() == 0;
        }

        public boolean isJoinClauseEmpty() {
            return this.joins.size() == 0 && this.joinArguments.size() == 0;
        }

        public boolean isOrderClauseEmpty() {
            return this.orderClause.size() == 0;
        }

        public boolean isEmpty() {
            return this.isAdditionalSelectClauseEmpty() && this.isWhereClauseEmpty() && this.isJoinClauseEmpty() && this.isOrderClauseEmpty();
        }

        public String toString() {
            return String.format("[%s], [%s] (joined with [%s], [%s], ordered with [%s])", this.getWhereClause(), this.getWhereClauseArguments(), this.getJoinClause(), this.getJoinArguments(), this.getOrderClause());
        }

        public String getAdditionalSelectClause() {
            if (this.additionalSelectClause.isEmpty()) return "";
            StringBuilder b = new StringBuilder();
            for (String alias : this.additionalSelectClause.keySet()) {
                b.append(", ");
                b.append(this.additionalSelectClause.get(alias));
                b.append(" AS ");
                b.append(alias);
            }
            return b.substring(1).trim();
        }

        public String getWhereClause() {
            return this.whereClause.toString();
        }

        public String getJoinClause() {
            StringBuilder b = new StringBuilder();
            for (String expr : this.joins.values()) {
                b.append(expr);
                b.append(" ");
            }
            return b.toString().trim();
        }

        public String getOrderClause() {
            return String.join(", ", this.orderClause);
        }

        public List<Object> getWhereClauseArguments() {
            return this.whereClauseArguments;
        }

        public List<Object> getJoinArguments() {
            return Arrays.asList(this.joinArguments.values().toArray());  // Should be sorted in the same order as the key
        }

        public List<Object> getAllArguments() {
            List<Object> args = new ArrayList<>();
            args.addAll(this.getJoinArguments());
            args.addAll(this.getWhereClauseArguments());
            return args;
        }

        public String getSelectClause(String tableName, String tableColumns) {

            StringBuilder sqlQuery = new StringBuilder();

            sqlQuery.append(String.format("SELECT %s", tableColumns));

            if (!this.isAdditionalSelectClauseEmpty())
                sqlQuery.append(String.format(", %s", this.getAdditionalSelectClause()));

            sqlQuery.append(String.format(" FROM %s", tableName));
            sqlQuery.append(String.format(" %s", this.getJoinClause()));
            sqlQuery.append(String.format(" WHERE %s", this.getWhereClause()));

            if (!this.isOrderClauseEmpty())
                sqlQuery.append(String.format(" ORDER BY %s", this.getOrderClause()));

            return sqlQuery.toString();
        }
    }

    public AdvancedSearchQuerySqlVisitor(String username) {
        this.username = username;
    }

    @Override
    public SqlClause visitQuery(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.QueryContext ctx) {
        if (ctx.expression() == null) throw new RuntimeException("Expected expression in " + ctx.getText());
        return visit(ctx.expression());
    }

    @Override
    public SqlClause visitBracketExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.BracketExpressionContext ctx) {
        if (ctx.expression() == null) throw new RuntimeException("Expected expression in " + ctx.getText());
        return visit(ctx.expression()).encloseWhere();
    }

    @Override
    public SqlClause visitPredicateExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.PredicateExpressionContext ctx) {

        List<org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.PredicateContext> predicates = ctx.predicate();
        if (predicates == null || predicates.size() == 0) throw new RuntimeException("Expected predicates in " + ctx.getText());

        SqlClause clause = new SqlClause();
        clause.addWhere("(");
        clause.addWhere(visit(predicates.get(0)));
        clause.addWhere(")");
        if (predicates.size() > 1) {
            for (org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.PredicateContext pctx : predicates.subList(1, predicates.size())) {
                clause.addWhere(" AND ");
                clause.addWhere("(");
                clause.addWhere(visit(pctx));
                clause.addWhere(")");
            }
        }

        return clause;
    }

    @Override
    public SqlClause visitAndExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.AndExpressionContext ctx) {
        List<org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.ExpressionContext> expressions = ctx.expression();
        if (expressions == null || expressions.size() == 0) throw new RuntimeException("Expected expressions in " + ctx.getText());

        SqlClause clause = new SqlClause();
        clause.addWhere("(");
        clause.addWhere(visit(expressions.get(0)));
        clause.addWhere(")");
        if (expressions.size() > 1) {
            for (org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.ExpressionContext pctx : expressions.subList(1, expressions.size())) {
                clause.addWhere(" AND ");
                clause.addWhere("(");
                clause.addWhere(visit(pctx));
                clause.addWhere(")");
            }
        }

        return clause;
    }

    @Override
    public SqlClause visitOrExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OrExpressionContext ctx) {
        List<org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.ExpressionContext> expressions = ctx.expression();
        if (expressions == null || expressions.size() == 0) throw new RuntimeException("Expected expressions in " + ctx.getText());

        SqlClause clause = new SqlClause();
        clause.addWhere("(");
        clause.addWhere(visit(expressions.get(0)));
        clause.addWhere(")");
        if (expressions.size() > 1) {
            for (org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.ExpressionContext pctx : expressions.subList(1, expressions.size())) {
                clause.addWhere(" OR ");
                clause.addWhere("(");
                clause.addWhere(visit(pctx));
                clause.addWhere(")");
            }
        }

        return clause;
    }

    @Override
    public SqlClause visitNotExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.NotExpressionContext ctx) {
        if (ctx.expression() == null) throw new RuntimeException("Expected expression in " + ctx.getText());
        SqlClause clause = new SqlClause();
        clause.addWhere("(NOT (");
        clause.addWhere(visit(ctx.expression()));
        clause.addWhere(")");
        return clause;
    }

    @Override
    public SqlClause visitBetweenPredicate(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.BetweenPredicateContext ctx) {

        if (ctx.field() == null) throw new RuntimeException("Expected field definition in " + ctx.getText());
        if (ctx.value(0) == null) throw new RuntimeException("Expected value definition in " + ctx.getText());
        if (ctx.value(1) == null) throw new RuntimeException("Expected value definition in " + ctx.getText());

        String field = ctx.field().getText();
        String value1 = ctx.value(0).getText();
        String value2 = ctx.value(1).getText();

        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        SqlClause clause = new SqlClause();
        clause.addWhere(String.format("%s IS NOT NULL AND %s >= ?", field, field), value1);
        clause.addWhere(String.format(" AND %s <= ?", field), value2);
        return clause;
    }

    @Override
    public SqlClause visitOperatorPredicate(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorPredicateContext ctx) {

        if (ctx.field() == null) throw new RuntimeException("Expected field definition in " + ctx.getText());
        if (ctx.value() == null) throw new RuntimeException("Expected value definition in " + ctx.getText());

        String fieldName = ctx.field().getText();
        String value = ctx.value().getText();

        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }

        return handleFieldValues(ctx.operator(), Field.get(fieldName), value);
    }

    private void addFieldSpecificClauses(Field field, SqlClause clause) {
        switch (field) {
            case ALBUM_RATING:
                clause.addJoin(
                        "media_album",
                        "left outer join media_file media_album on media_album.type = 'ALBUM' and media_album.path = media_file.parent_path");
                clause.addJoin(
                        "user_rating",
                        "left outer join user_rating on user_rating.path = media_album.path and user_rating.username = ?",
                        this.username);
                break;
            case STARRED:
                clause.addJoin(
                        "starred_media_file",
                        "left outer join starred_media_file on media_file.id = starred_media_file.media_file_id and starred_media_file.username = ?",
                        this.username);
                break;
            case _RANKING:
                addFieldSpecificClauses(Field.ALBUM_RATING, clause);
                addFieldSpecificClauses(Field.STARRED, clause);
                clause.addAdditionalSelect("_RANKING_DS", "(CASE WHEN DATEDIFF(DAY, starred_media_file.CREATED, NOW()) <= 0 THEN 1 ELSE DATEDIFF(DAY, starred_media_file.CREATED, NOW()) END)");
                clause.addAdditionalSelect("_RANKING_DC", "(CASE WHEN DATEDIFF(DAY, CREATED, NOW()) <= 0 THEN 1 ELSE DATEDIFF(DAY, CREATED, NOW()) END)");
                clause.addAdditionalSelect("_RANKING_DL", "(CASE WHEN DATEDIFF(DAY, LAST_PLAYED, NOW()) <= 0 THEN 1 ELSE DATEDIFF(DAY, LAST_PLAYED, NOW()) END)");
                clause.addAdditionalSelect("_RANKING_PC", "MEDIA_FILE.PLAY_COUNT");
                clause.addAdditionalSelect("_RANKING_APC", "MEDIA_ALBUM.PLAY_COUNT");
                clause.addAdditionalSelect("_RANKING_R", "IFNULL(USER_RATING.RATING, 2)");
            default:
                break;
        }
    }

    private void addOrderClause(Field field, SqlClause clause, boolean ascending) {
        addFieldSpecificClauses(field, clause);
        switch (field) {
            case _RANKING:
                clause.addOrder("POWER(_RANKING_R,2) * POWER(_RANKING_PC + 0.5*_RANKING_APC, 1) * POWER(_RANKING_DS, -1) * POWER(_RANKING_DC, -1) * POWER(_RANKING_DL, 0)", ascending);
                break;
            default:
                clause.addOrder(field.getSqlFullColumn(), ascending);
                break;
        }
    }

    private SqlClause handleFieldValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, Field field, String value) {
        SqlClause clause;
        switch (field) {
            case PATH:
            case TITLE:
            case ALBUM:
            case ARTIST:
            case ALBUM_ARTIST:
            case COMMENT:
            case MB_RELEASE_ID:
            case MB_RECORDING_ID:
            case GENRE:
            case FOLDER:
                clause = handleStringValues(ctx, field, value);
                break;
            case DISC_NUMBER:
            case TRACK_NUMBER:
            case BIT_RATE:
            case DURATION:
            case FILE_SIZE:
            case YEAR:
            case PLAY_COUNT:
            case ALBUM_RATING:
                clause = handleIntValues(ctx, field, value);
                break;
            case CREATED:
            case CHANGED:
            case LAST_SCANNED:
            case LAST_PLAYED:
                clause = handleDateValues(ctx, field, value);
                break;
            case FORMAT:
                clause = handleExactStringValues(ctx, field, value);
                break;
            case STARRED:
                clause = handleBoolValues(ctx, field, value);
                break;
            default:
                throw new RuntimeException("Unexpected field name: " + field);
        }
        addFieldSpecificClauses(field, clause);
        return clause;
    }

    private SqlClause handleDateValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, Field field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null) {
            return new SqlClause(String.format("%s = ?", field.getSqlFullColumn()), value);
        } else if (ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlClause(String.format("%s IS NULL", field.getSqlFullColumn()));
            } else {
                return new SqlClause(String.format("%s = ?", field.getSqlFullColumn()), value);
            }
        } else if (ctx.LT() != null) {
            return new SqlClause(String.format("%s IS NULL OR %s < ?", field.getSqlFullColumn(), field.getSqlFullColumn()), value);
        } else if (ctx.GT() != null) {
            return new SqlClause(String.format("%s > ?", field.getSqlFullColumn()), value);
        } else if (ctx.LTE() != null) {
            return new SqlClause(String.format("%s IS NULL OR %s <= ?", field.getSqlFullColumn(), field.getSqlFullColumn()), value);
        } else if (ctx.GTE() != null) {
            return new SqlClause(String.format("%s >= ?", field.getSqlFullColumn()), value);
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for Date types: " + ctx.getText());
        }
    }

    private SqlClause handleIntValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, Field field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null) {
            return new SqlClause(String.format("%s = ?", field.getSqlFullColumn()), value);
        } else if (ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlClause(String.format("%s IS NULL", field.getSqlFullColumn()));
            } else {
                return new SqlClause(String.format("%s = ?", field.getSqlFullColumn()), value);
            }
        } else if (ctx.LT() != null) {
            return new SqlClause(String.format("%s IS NULL OR %s < ?", field.getSqlFullColumn(), field.getSqlFullColumn()), value);
        } else if (ctx.GT() != null) {
            return new SqlClause(String.format("%s > ?", field.getSqlFullColumn()), value);
        } else if (ctx.LTE() != null) {
            return new SqlClause(String.format("%s IS NULL OR %s <= ?", field.getSqlFullColumn(), field.getSqlFullColumn()), value);
        } else if (ctx.GTE() != null) {
            return new SqlClause(String.format("%s >= ?", field.getSqlFullColumn()), value);
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for Integer types: " + ctx.getText());
        }
    }

    private SqlClause handleBoolValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, Field field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null || ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlClause(String.format("%s IS NULL", field.getSqlFullColumn()));
            } else if ("true".equalsIgnoreCase(value) || "t".equalsIgnoreCase(value) || "1".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "y".equalsIgnoreCase(value)) {
                return new SqlClause(String.format("%s IS NOT NULL", field.getSqlFullColumn()));
            } else if ("false".equalsIgnoreCase(value) || "f".equalsIgnoreCase(value) || "0".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "n".equalsIgnoreCase(value)) {
                return new SqlClause(String.format("%s IS NULL", field.getSqlFullColumn()));
            } else {
                throw new RuntimeException("Unknown boolean value: " + value);
            }
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for Boolean types: " + ctx.getText());
        }
    }

    private SqlClause handleExactStringValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, Field field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null) {
            return new SqlClause(String.format("%s = ?", field.getSqlFullColumn()), value);
        } else if (ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlClause(String.format("%s IS NULL", field.getSqlFullColumn()));
            } else {
                return new SqlClause(String.format("%s = ?", field.getSqlFullColumn()), value);
            }
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for String types (exact match): " + ctx.getText());
        }
    }

    private SqlClause handleStringValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, Field field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null) {
            return new SqlClause(String.format("%s = ?", field.getSqlFullColumn()), value);
        } else if (ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlClause(String.format("%s IS NULL", field.getSqlFullColumn()));
            } else {
                value = "%" + value.toLowerCase() + "%";
                return new SqlClause(String.format("lcase(%s) LIKE ?", field.getSqlFullColumn()), value.toLowerCase());
            }
        } else if (ctx.REGEXP() != null) {
            return new SqlClause(String.format("regexp_matches(%s, ?)", field.getSqlFullColumn()), value);
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for String types: " + ctx.getText());
        }
    }

    private SqlClause parseSearchQuery(String query) throws AdvancedSearchQueryParseError {
        // Create the ANTLR parser instance and parse the query
        org.airsonic.player.service.search.parser.AdvancedSearchQueryLexer lexer = new org.airsonic.player.service.search.parser.AdvancedSearchQueryLexer(CharStreams.fromString(query));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        org.airsonic.player.service.search.parser.AdvancedSearchQueryParser parser = new org.airsonic.player.service.search.parser.AdvancedSearchQueryParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ThrowingErrorListener());
        try {
            return this.visit(parser.query());
        } catch (ParseCancellationException e) {
            throw new AdvancedSearchQueryParseError(e.getMessage());
        }
    }

    private SqlClause parseOrderQuery(String orderQuery) throws AdvancedSearchQueryParseError {

        SqlClause clause = new SqlClause();

        if (orderQuery == null) return clause;

        for (String orderColumn : orderQuery.trim().split("\\s+,\\s+")) {

            // Find field name
            orderColumn = orderColumn.trim();
            boolean ascending = !orderColumn.startsWith("-");
            orderColumn = orderColumn.substring(!ascending ? 1 : 0).trim().toLowerCase();
            if (orderColumn.isEmpty()) continue;

            // Convert that to a field instance
            Field field;
            try {
                field = Field.get(orderColumn);
            } catch (Exception e) {
                throw new AdvancedSearchQueryParseError(e.getMessage());
            }

            // Add the ordering
            addOrderClause(field, clause, ascending);
        }

        return clause;
    }

    private static class ThrowingErrorListener extends BaseErrorListener {
        private static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e)
                throws ParseCancellationException {
            throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }

    public SqlClause parse(String query, String order) throws AdvancedSearchQueryParseError {
        SqlClause searchClause = parseSearchQuery(query);
        SqlClause orderClause = parseOrderQuery(order);
        searchClause.addOrder(orderClause);
        return searchClause;
    }

    public static SqlClause toSql(String username, String expr) throws AdvancedSearchQueryParseError {
        return toSql(username, expr, null);
    }

    /**
     * Parse an advanced search query with an optional ordering expression
     *
     * For example:
     *
     * AdvancedSearchQuerySqlMusicVisitor.toSql("fx", "artist:椎名林檎 starred:y", "-last_played");
     */
    public static SqlClause toSql(String username, String expr, String orderExpr) throws AdvancedSearchQueryParseError {
        return (new AdvancedSearchQuerySqlVisitor(username)).parse(expr, orderExpr);
    }
}
