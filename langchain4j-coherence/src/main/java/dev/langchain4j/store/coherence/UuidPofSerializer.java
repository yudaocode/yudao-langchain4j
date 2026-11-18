package dev.langchain4j.store.coherence;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofSerializer;
import com.tangosol.io.pof.PofWriter;
import java.io.IOException;
import java.util.UUID;

/**
 * Preserves UUID metadata values in a Coherence 14.1 POF cache.
 *
 * @author 芋道源码
 */
public final class UuidPofSerializer implements PofSerializer<UUID> {

    @Override
    public void serialize(PofWriter writer, UUID uuid) throws IOException {
        writer.writeLong(0, uuid.getMostSignificantBits());
        writer.writeLong(1, uuid.getLeastSignificantBits());
        writer.writeRemainder(null);
    }

    @Override
    public UUID deserialize(PofReader reader) throws IOException {
        UUID uuid = new UUID(reader.readLong(0), reader.readLong(1));
        reader.readRemainder();
        return uuid;
    }
}
