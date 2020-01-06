package org.airsonic.player.service.search.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AdvancedSearchQuerySqlVisitor extends org.airsonic.player.service.search.parser.AdvancedSearchQueryBaseVisitor<AdvancedSearchQuerySqlVisitor.SqlWhereClause> {

    private String username;

    public static class SqlWhereClause {
        private StringBuilder clause;
        private List<Object> clauseArguments;
        private Map<String, String> joins;
        private List<Object> joinArguments;

        public SqlWhereClause() {
            this.clause = new StringBuilder();
            this.clauseArguments = new ArrayList<>();
            this.joins = new TreeMap<>();
            this.joinArguments = new ArrayList<>();
        }

        public SqlWhereClause(String expr) {
            this.clause = new StringBuilder();
            this.clauseArguments = new ArrayList<>();
            this.joins = new TreeMap<>();
            this.joinArguments = new ArrayList<>();
            this.add(expr);
        }

        public SqlWhereClause(String expr, Object arg) {
            this.clause = new StringBuilder();
            this.clauseArguments = new ArrayList<>();
            this.joins = new TreeMap<>();
            this.joinArguments = new ArrayList<>();
            this.add(expr, arg);
        }

        public void add(String expr) {
            this.clause.append(expr);
        }

        public void add(String expr, Object arg) {
            if (!expr.contains("?")) throw new RuntimeException("Expression " + expr + " must contain at least one argument");
            this.clause.append(expr);
            this.clauseArguments.add(arg);
        }

        public void add(SqlWhereClause other) {
            this.clause.append(other.clause);
            this.clauseArguments.addAll(other.clauseArguments);
            this.joins.putAll(other.joins);
            this.joinArguments.addAll(other.joinArguments);
        }

        public void join(String alias, String expr) {
            if (this.joins.containsKey(alias) && !this.joins.get(alias).equals(expr)) {
                throw new RuntimeException("Already joined: " + expr);
            }
            this.joins.put(alias, expr);
        }

        public void join(String alias, String expr, Object argument) {
            this.join(alias, expr);
            this.joinArguments.add(argument);
        }

        public String toString() {
            return String.format("[%s], [%s] (joined with [%s], [%s])", this.getWhereClause(), this.getClauseArguments(), this.getJoinClause(), this.getJoinArguments());
        }

        public String getWhereClause() {
            return this.clause.toString();
        }

        public String getJoinClause() {
            StringBuilder b = new StringBuilder();
            for (String expr : this.joins.values()) {
                b.append(expr);
                b.append(" ");
            }
            return b.toString().trim();
        }

        public List<Object> getClauseArguments() {
            return this.clauseArguments;
        }

        public List<Object> getJoinArguments() {
            return this.joinArguments;
        }

        public List<Object> getAllArguments() {
            List<Object> args = new ArrayList<>();
            args.addAll(this.joinArguments);
            args.addAll(this.clauseArguments);
            return args;
        }

        public String getSelectClause(String tableName, String tableColumns) {
            return String.format(
                "SELECT %s FROM %s %s WHERE %s",
                tableColumns,
                tableName,
                this.getJoinClause(),
                this.getWhereClause()
            );
        }
    }

    public AdvancedSearchQuerySqlVisitor(String username) {
        this.username = username;
    }

    @Override
    public SqlWhereClause visitQuery(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.QueryContext ctx) {
        if (ctx.expression() == null) throw new RuntimeException("Expected expression in " + ctx.getText());
        return visit(ctx.expression());
    }

    @Override
    public SqlWhereClause visitBracketExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.BracketExpressionContext ctx) {
        if (ctx.expression() == null) throw new RuntimeException("Expected expression in " + ctx.getText());
        SqlWhereClause clause = new SqlWhereClause();
        clause.add("(");
        clause.add(visit(ctx.expression()));
        clause.add(")");
        return clause;
    }

    @Override
    public SqlWhereClause visitPredicateExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.PredicateExpressionContext ctx) {

        List<org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.PredicateContext> predicates = ctx.predicate();
        if (predicates == null || predicates.size() == 0) throw new RuntimeException("Expected predicates in " + ctx.getText());

        SqlWhereClause clause = new SqlWhereClause();
        clause.add("(");
        clause.add(visit(predicates.get(0)));
        clause.add(")");
        if (predicates.size() > 1) {
            for (org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.PredicateContext pctx : predicates.subList(1, predicates.size())) {
                clause.add(" AND ");
                clause.add("(");
                clause.add(visit(pctx));
                clause.add(")");
            }
        }

        return clause;
    }

    @Override
    public SqlWhereClause visitAndExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.AndExpressionContext ctx) {
        List<org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.ExpressionContext> expressions = ctx.expression();
        if (expressions == null || expressions.size() == 0) throw new RuntimeException("Expected expressions in " + ctx.getText());

        SqlWhereClause clause = new SqlWhereClause();
        clause.add("(");
        clause.add(visit(expressions.get(0)));
        clause.add(")");
        if (expressions.size() > 1) {
            for (org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.ExpressionContext pctx : expressions.subList(1, expressions.size())) {
                clause.add(" AND ");
                clause.add("(");
                clause.add(visit(pctx));
                clause.add(")");
            }
        }

        return clause;
    }

    @Override
    public SqlWhereClause visitOrExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OrExpressionContext ctx) {
        List<org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.ExpressionContext> expressions = ctx.expression();
        if (expressions == null || expressions.size() == 0) throw new RuntimeException("Expected expressions in " + ctx.getText());

        SqlWhereClause clause = new SqlWhereClause();
        clause.add("(");
        clause.add(visit(expressions.get(0)));
        clause.add(")");
        if (expressions.size() > 1) {
            for (org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.ExpressionContext pctx : expressions.subList(1, expressions.size())) {
                clause.add(" OR ");
                clause.add("(");
                clause.add(visit(pctx));
                clause.add(")");
            }
        }

        return clause;
    }

    @Override
    public SqlWhereClause visitNotExpression(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.NotExpressionContext ctx) {
        if (ctx.expression() == null) throw new RuntimeException("Expected expression in " + ctx.getText());
        SqlWhereClause clause = new SqlWhereClause();
        clause.add("(NOT (");
        clause.add(visit(ctx.expression()));
        clause.add(")");
        return clause;
    }

    @Override
    public SqlWhereClause visitBetweenPredicate(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.BetweenPredicateContext ctx) {

        if (ctx.field() == null) throw new RuntimeException("Expected field definition in " + ctx.getText());
        if (ctx.value(0) == null) throw new RuntimeException("Expected value definition in " + ctx.getText());
        if (ctx.value(1) == null) throw new RuntimeException("Expected value definition in " + ctx.getText());

        String field = ctx.field().getText();
        String value1 = ctx.value(0).getText();
        String value2 = ctx.value(1).getText();

        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        SqlWhereClause clause = new SqlWhereClause();
        clause.add(String.format("%s IS NOT NULL AND %s >= ?", field, field), value1);
        clause.add(String.format(" AND %s <= ?", field), value2);
        return clause;
    }

    @Override
    public SqlWhereClause visitOperatorPredicate(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorPredicateContext ctx) {

        if (ctx.field() == null) throw new RuntimeException("Expected field definition in " + ctx.getText());
        if (ctx.value() == null) throw new RuntimeException("Expected value definition in " + ctx.getText());

        String field = ctx.field().getText();
        String value = ctx.value().getText();
        String sqlField = field;

        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }

        switch (field) {
            case "path":
                sqlField = "media_file.path";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "title":
                sqlField = "media_file.title";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "album":
                sqlField = "media_file.album";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "artist":
                sqlField = "media_file.artist";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "albumartist":
                sqlField = "media_file.album_artist";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "discnumber":
                sqlField = "media_file.disc_number";
                return handleIntValues(ctx.operator(), sqlField, value);
            case "tracknumber":
                sqlField = "media_file.track_number";
                return handleIntValues(ctx.operator(), sqlField, value);
            case "bitrate":
                sqlField = "media_file.bit_rate";
                return handleIntValues(ctx.operator(), sqlField, value);
            case "duration":
                sqlField = "media_file.variable_bit_rate";
                return handleIntValues(ctx.operator(), sqlField, value);
            case "filesize":
                sqlField = "media_file.file_size";
                return handleIntValues(ctx.operator(), sqlField, value);
            case "comment":
                sqlField = "media_file.comment";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "created":
                sqlField = "media_file.created";
                return handleDateValues(ctx.operator(), sqlField, value);
            case "changed":
                sqlField = "media_file.changed";
                return handleDateValues(ctx.operator(), sqlField, value);
            case "lastscanned":
                sqlField = "media_file.last_scanned";
                return handleDateValues(ctx.operator(), sqlField, value);
            case "mb_release_id":
                sqlField = "media_file.mb_release_id";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "mb_recording_id":
                sqlField = "media_file.mb_recording_id";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "genre":
                sqlField = "media_file.genre";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "year":
                sqlField = "media_file.year";
                return handleIntValues(ctx.operator(), sqlField, value);
            case "folder":
                sqlField = "media_file.folder";
                return handleStringValues(ctx.operator(), sqlField, value);
            case "lastplayed":
                sqlField = "media_file.last_played";
                return handleDateValues(ctx.operator(), sqlField, value);
            case "format":
                sqlField = "media_file.format";
                return handleExactStringValues(ctx.operator(), sqlField, value);
            case "playcount":
                sqlField = "media_file.play_count";
                return handleIntValues(ctx.operator(), sqlField, value);
            case "albumrating":
                sqlField = "user_rating.rating";
                SqlWhereClause albumRatingClause = handleIntValues(ctx.operator(), sqlField, value);
                albumRatingClause.join(
                    "media_album",
                    "left outer join media_file media_album on media_album.type = 'ALBUM' and media_album.album = media_file.album and media_album.artist = media_file.artist");
                albumRatingClause.join(
                    "user_rating",
                    "left outer join user_rating on user_rating.path = media_album.path and user_rating.username = ?",
                    this.username);
                return albumRatingClause;
            case "starred":
                sqlField = "starred_media_file.id";
                SqlWhereClause starredClause = handleBoolValues(ctx.operator(), sqlField, value);
                starredClause.join(
                    "starred_media_file",
                    "left outer join starred_media_file on media_file.id = starred_media_file.media_file_id and starred_media_file.username = ?",
                    this.username);
                return starredClause;
            default:
                throw new RuntimeException("Unexpected field name: " + field);
        }
    }

    private SqlWhereClause handleDateValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, String field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null) {
            return new SqlWhereClause(String.format("%s = ?", field), value);
        } else if (ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlWhereClause(String.format("%s IS NULL", field));
            } else {
                return new SqlWhereClause(String.format("%s = ?", field), value);
            }
        } else if (ctx.LT() != null) {
            return new SqlWhereClause(String.format("%s IS NULL OR %s < ?", field, field), value);
        } else if (ctx.GT() != null) {
            return new SqlWhereClause(String.format("%s > ?", field), value);
        } else if (ctx.LTE() != null) {
            return new SqlWhereClause(String.format("%s IS NULL OR %s <= ?", field, field), value);
        } else if (ctx.GTE() != null) {
            return new SqlWhereClause(String.format("%s >= ?", field), value);
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for Date types: " + ctx.getText());
        }
    }

    private SqlWhereClause handleIntValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, String field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null) {
            return new SqlWhereClause(String.format("%s = ?", field), value);
        } else if (ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlWhereClause(String.format("%s IS NULL", field));
            } else {
                return new SqlWhereClause(String.format("%s = ?", field), value);
            }
        } else if (ctx.LT() != null) {
            return new SqlWhereClause(String.format("%s IS NULL OR %s < ?", field, field), value);
        } else if (ctx.GT() != null) {
            return new SqlWhereClause(String.format("%s > ?", field), value);
        } else if (ctx.LTE() != null) {
            return new SqlWhereClause(String.format("%s IS NULL OR %s <= ?", field, field), value);
        } else if (ctx.GTE() != null) {
            return new SqlWhereClause(String.format("%s >= ?", field), value);
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for Integer types: " + ctx.getText());
        }
    }

    private SqlWhereClause handleBoolValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, String field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null || ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlWhereClause(String.format("%s IS NULL", field));
            } else if ("true".equalsIgnoreCase(value) || "t".equalsIgnoreCase(value) || "1".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "y".equalsIgnoreCase(value)) {
                return new SqlWhereClause(String.format("%s IS NOT NULL", field));
            } else if ("false".equalsIgnoreCase(value) || "f".equalsIgnoreCase(value) || "0".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "n".equalsIgnoreCase(value)) {
                return new SqlWhereClause(String.format("%s IS NULL", field));
            } else {
                throw new RuntimeException("Unknown boolean value: " + value);
            }
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for Boolean types: " + ctx.getText());
        }
    }

    private SqlWhereClause handleExactStringValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, String field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null) {
            return new SqlWhereClause(String.format("%s = ?", field), value);
        } else if (ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlWhereClause(String.format("%s IS NULL", field));
            } else {
                return new SqlWhereClause(String.format("%s = ?", field), value);
            }
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for String types (exact match): " + ctx.getText());
        }
    }

    private SqlWhereClause handleStringValues(org.airsonic.player.service.search.parser.AdvancedSearchQueryParser.OperatorContext ctx, String field, String value) {
        // TODO: Blork, change that to avoid SQLi
        // TODO: Support column types
        if (ctx.EQ() != null) {
            return new SqlWhereClause(String.format("%s = ?", field), value);
        } else if (ctx.FUZZEQ() != null) {
            if ("null".equalsIgnoreCase(value)) {
                return new SqlWhereClause(String.format("%s IS NULL", field));
            } else {
                value = "%" + value.toLowerCase() + "%";
                return new SqlWhereClause(String.format("lcase(%s) LIKE ?", field), value.toLowerCase());
            }
        } else if (ctx.REGEXP() != null) {
            return new SqlWhereClause(String.format("regexp_matches(%s, ?)", field), value);
        } else {
            // TODO: Change that to a correct exception class
            throw new RuntimeException("Unknown operator for String types: " + ctx.getText());
        }
    }

    public static class ThrowingErrorListener extends BaseErrorListener {
        public static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e)
                throws ParseCancellationException {
            throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }

    public static SqlWhereClause toSql(String username, String expr) {
        org.airsonic.player.service.search.parser.AdvancedSearchQueryLexer lexer = new org.airsonic.player.service.search.parser.AdvancedSearchQueryLexer(CharStreams.fromString(expr));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        org.airsonic.player.service.search.parser.AdvancedSearchQueryParser parser = new org.airsonic.player.service.search.parser.AdvancedSearchQueryParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ThrowingErrorListener());
        return new AdvancedSearchQuerySqlVisitor(username).visit(parser.query());
    }
}
