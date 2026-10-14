package lesotho.igss;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Thin JDBC helper for SQLite. Every SQL statement in the project goes through here. */
final class Db {
    private Db() {}

    interface Mapper<T> { T map(ResultSet r) throws SQLException; }

    private static Connection conn;

    static void open(String file) {
        try { conn = DriverManager.getConnection("jdbc:sqlite:" + file); }
        catch (SQLException e) { throw new RuntimeException("Cannot open database. Is the SQLite driver in the lib folder?", e); }
    }

    private static void bind(PreparedStatement s, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) {
            Object v = p[i];
            if (v instanceof Boolean) s.setInt(i + 1, ((Boolean) v) ? 1 : 0);
            else if (v != null && !(v instanceof String) && !(v instanceof Number)) s.setString(i + 1, v.toString());
            else s.setObject(i + 1, v);
        }
    }

    static void exec(String sql, Object... p) {
        try (PreparedStatement s = conn.prepareStatement(sql)) { bind(s, p); s.executeUpdate(); }
        catch (SQLException e) { throw new RuntimeException(sql, e); }
    }

    static <T> List<T> list(String sql, Mapper<T> m, Object... p) {
        List<T> out = new ArrayList<>();
        try (PreparedStatement s = conn.prepareStatement(sql)) {
            bind(s, p);
            try (ResultSet r = s.executeQuery()) { while (r.next()) out.add(m.map(r)); }
        } catch (SQLException e) { throw new RuntimeException(sql, e); }
        return out;
    }

    static <T> T one(String sql, Mapper<T> m, Object... p) { List<T> l = list(sql, m, p); return l.isEmpty() ? null : l.get(0); }

    static int count(String sql, Object... p) { Integer n = one(sql, r -> r.getInt(1), p); return n == null ? 0 : n; }

    static int insert(String sql, Object... p) { exec(sql, p); return one("select last_insert_rowid()", r -> r.getInt(1)); }
}
