package dev.langchain4j.store.embedding.hibernate.pgvector;

import com.pgvector.PGhalfvec;
import com.pgvector.PGvector;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Properties;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

/**
 * Binds float arrays to pgvector columns.
 * Set the Hibernate type parameter {@code storage=halfvec} for half-precision columns.
 *
 * @author 芋道源码
 */
public final class PgVectorType implements UserType, ParameterizedType {

    private Storage storage = Storage.VECTOR;

    @Override
    public void setParameterValues(Properties parameters) {
        String configured = parameters.getProperty("storage", "vector");
        for (Storage candidate : Storage.values()) {
            if (candidate.sqlType.equals(configured)) {
                storage = candidate;
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported pgvector storage: " + configured);
    }

    Storage storage() {
        return storage;
    }

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
        return value == null ? null : storage.read(value);
    }

    @Override
    public void nullSafeSet(PreparedStatement statement, Object value, int position,
            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            statement.setNull(position, Types.OTHER);
        } else {
            statement.setObject(position, storage.bind((float[]) value));
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

    /**
     * @author 芋道源码
     */
    enum Storage {
        VECTOR("vector", "lc_"),
        HALFVEC("halfvec", "lc_halfvec_");

        final String sqlType;
        final String functionPrefix;

        Storage(String sqlType, String functionPrefix) {
            this.sqlType = sqlType;
            this.functionPrefix = functionPrefix;
        }

        PGobject bind(float[] value) {
            return this == HALFVEC ? new PGhalfvec(value) : new PGvector(value);
        }

        float[] read(String value) throws SQLException {
            return this == HALFVEC ? new PGhalfvec(value).toArray() : new PGvector(value).toArray();
        }
    }

}
