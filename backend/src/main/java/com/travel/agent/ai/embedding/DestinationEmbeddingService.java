package com.travel.agent.ai.embedding;

import com.travel.agent.ai.vectorstore.ChromaService;
import com.travel.agent.entity.Destinations;
import com.travel.agent.service.DestinationsService;
import dev.langchain4j.data.document.Metadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationEmbeddingService {

    private final DestinationsService destinationsService;
    private final ChromaService chromaService;

    /**
     * 向量化单个目的地
     */
    public String embedDestination(Destinations destination) {
        // 1. 构建文本表示
        String text = buildDestinationText(destination);
        
        // 2. 构建元数据
        Metadata metadata = Metadata.from("destination_id", String.valueOf(destination.getId()))
                .put("name", destination.getName())
                .put("country", destination.getCountry())
                .put("state", destination.getState())
                .put("budget_level", destination.getBudgetLevel())
                .put("latitude", destination.getLatitude().toString())
                .put("longitude", destination.getLongitude().toString());
        
        // 3. 添加到向量数据库
        return chromaService.addText(text, metadata);
    }

    /**
     * 批量向量化所有目的地
     */
    public void embedAllDestinations() {
        log.info("🚀 Starting to embed all destinations...");
        
        // 1. 获取所有目的地
        List<Destinations> destinations = destinationsService.list();
        log.info("Found {} destinations to embed", destinations.size());
        
        // 2. 准备批量数据
        List<String> texts = new ArrayList<>();
        List<Metadata> metadataList = new ArrayList<>();
        
        for (Destinations destination : destinations) {
            texts.add(buildDestinationText(destination));
            
            Metadata metadata = Metadata.from("destination_id", String.valueOf(destination.getId()))
                    .put("name", destination.getName())
                    .put("country", destination.getCountry())
                    .put("state", destination.getState())
                    .put("budget_level", destination.getBudgetLevel())
                    .put("latitude", destination.getLatitude().toString())
                    .put("longitude", destination.getLongitude().toString());
            
            metadataList.add(metadata);
        }
        
        // 3. 批量添加
        List<String> ids = chromaService.addTexts(texts, metadataList);
        
        log.info("✅ Successfully embedded {} destinations", ids.size());
    }

    /**
     * 构建目的地的文本表示（用于 Embedding）
     */
    private String buildDestinationText(Destinations destination) {
        StringBuilder sb = new StringBuilder();
        
        // 名称和位置
        sb.append("Destination: ").append(destination.getName());
        if (destination.getState() != null) {
            sb.append(", ").append(destination.getState());
        }
        sb.append(", ").append(destination.getCountry()).append(". ");
        
        // 描述
        if (destination.getDescription() != null) {
            sb.append(destination.getDescription()).append(" ");
        }
        
        // 预算等级
        String budgetLabel = switch (destination.getBudgetLevel()) {
            case 1 -> "Budget-friendly";
            case 2 -> "Moderate budget";
            case 3 -> "Luxury";
            default -> "Moderate budget";
        };
        sb.append("Budget level: ").append(budgetLabel).append(". ");
        
        // 最佳季节
        if (destination.getBestSeason() != null) {
            sb.append("Best season: ").append(destination.getBestSeason()).append(". ");
        }
        
        return sb.toString().trim();
    }
}
