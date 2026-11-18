package dev.langchain4j.store.embedding.hibernate.pgvector;

import com.pgvector.PGvector;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * Binds float arrays using PostgreSQL's native vector type.
 *
 * @author 芋道源码
 */
public final class PgVectorType implements UserType {
    @Override
    public int[] sqlTypes() {
        return new int[] {Types.OTHER};
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(Object left, Object right) {
        return Arrays.equals((float[]) left, (float[]) right);
    }

    @Override
    public int hashCode(Object value) {
        return Arrays.hashCode((float[]) value);
    }

    @Override
    public Object nullSafeGet(ResultSet result, String[] columns, SharedSessionContractImplementor session,
            Object owner) throws SQLException {
        String value = result.getString(columns[0]);
        return value == null ? null : new PGvector(value).toArray();
    }

    @Override
    public void nullSafeSet(PreparedStatement statement, Object value, int position,
            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            statement.setNull(position, Types.OTHER);
        } else {
            statement.setObject(position, new PGvector((float[]) value));
        }
    }

    @Override
    public Object deepCopy(Object value) {
        return value == null ? null : ((float[]) value).clone();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(Object value) {
        return (Serializable) deepCopy(value);
    }

    @Override
    public Object assemble(Serializable value, Object owner) {
        return deepCopy(value);
    }

    @Override
    public Object replace(Object original, Object target, Object owner) {
        return deepCopy(original);
    }
}
