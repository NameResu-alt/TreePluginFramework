package org.treepluginframework.values;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Map;

public class TPFMetadataFileSerializer extends JsonSerializer<TPFMetadataFile> {
    @Override
    public void serialize(TPFMetadataFile metadata, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        System.out.println("Got into serialize, lets see");
        gen.writeStartObject();

        // Write each class metadata under its class name
        for (Map.Entry<String, ClassValueMetadata> entry : metadata.classes.entrySet()) {
            gen.writeObjectField(entry.getKey(), entry.getValue());
        }
        System.out.println("Got into the end of serialize");

        gen.writeEndObject();
    }
}
