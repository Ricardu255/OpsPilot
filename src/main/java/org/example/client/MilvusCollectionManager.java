package org.example.client;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import org.example.constant.MilvusConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MilvusCollectionManager {

    private static final Logger logger = LoggerFactory.getLogger(MilvusCollectionManager.class);

    private final AtomicBoolean collectionLoaded = new AtomicBoolean(false);

    public void ensureCollectionLoaded(MilvusServiceClient milvusClient) {
        if (collectionLoaded.get()) {
            return;
        }
        synchronized (this) {
            if (collectionLoaded.get()) {
                return;
            }
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );
            if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
                throw new RuntimeException("加载 collection 失败: " + loadResponse.getMessage());
            }
            collectionLoaded.set(true);
            logger.info("Milvus collection '{}' loaded successfully", MilvusConstants.MILVUS_COLLECTION_NAME);
        }
    }
}
