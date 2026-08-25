package com.example.ragassistant.ingestion;

import com.example.ragassistant.config.ChunkingProperties;
import com.example.ragassistant.config.MetadataKeys;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChunkBuilder {

    private final ChunkingProperties chunkingProps;

    public ChunkBuilder(ChunkingProperties chunkingProps) {
        this.chunkingProps = chunkingProps;
    }

    public TokenTextSplitter createParentSplitter() {
        return new TokenTextSplitter(
                chunkingProps.getParent().getChunkSize(),
                chunkingProps.getParent().getMinChunkSize(),
                chunkingProps.getParent().getMinChunkSize(),
                chunkingProps.getParent().getChunkOverlap(),
                true
        );
    }

    public TokenTextSplitter createChildSplitter() {
        return new TokenTextSplitter(
                chunkingProps.getChild().getChunkSize(),
                chunkingProps.getChild().getMinChunkSize(),
                chunkingProps.getChild().getMinChunkSize(),
                chunkingProps.getChild().getChunkOverlap(),
                true
        );
    }

    /**
     * Splits parent document into parent chunks, then each parent into child chunks,
     * embedding the parent text and common metadata into every child chunk.
     */
    public List<Document> buildParentChildChunks(
            Document sourceDoc,
            Map<String, Object> baseMetadata) {

        TokenTextSplitter parentSplitter = createParentSplitter();
        TokenTextSplitter childSplitter = createChildSplitter();

        List<Document> parentChunks = parentSplitter.apply(List.of(sourceDoc));
        List<Document> finalChildChunks = new ArrayList<>();

        for (Document parent : parentChunks) {
            String parentContent = parent.getContent();
            List<Document> childChunks = childSplitter.apply(List.of(parent));

            for (Document child : childChunks) {
                Map<String, Object> metadata = new HashMap<>(baseMetadata);
                if (parent.getMetadata() != null) {
                    metadata.putAll(parent.getMetadata());
                }
                metadata.put(MetadataKeys.PARENT_TEXT, parentContent);
                metadata.put(MetadataKeys.CHUNK_TYPE, "child");

                finalChildChunks.add(new Document(child.getContent(), metadata));
            }
        }
        return finalChildChunks;
    }
}