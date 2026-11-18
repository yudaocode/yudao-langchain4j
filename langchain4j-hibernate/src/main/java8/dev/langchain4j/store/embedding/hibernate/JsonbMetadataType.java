package dev.langchain4j.store.embedding.hibernate;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

/**
 * Maps a JSON string to a PostgreSQL jsonb column for Hibernate 5.
 *
 * @author 芋道源码
 */
public final class JsonbMetadataType implements UserType {

    @Override
    public int[] sqlTypes() {
        return new int[] {Types.OTHER};
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(Object left, Object right) {
        return Objects.equals(left, right);
    }

    @Override
    public int hashCode(Object value) {
        return Objects.hashCode(value);
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] columns, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        return rs.getString(columns[0]);
    }

    @Override
    public void nullSafeSet(PreparedStatement statement, Object value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, postgresValue((String) value));
        }
    }

    static PGobject postgresValue(String value) throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(value);
        return json;
    }

    @Override
    public Object deepCopy(Object value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Object value) {
        return (String) value;
    }

    @Override
    public Object assemble(Serializable value, Object owner) {
        return value;
    }

    @Override
    public Object replace(Object original, Object target, Object owner) {
        return original;
    }
}
