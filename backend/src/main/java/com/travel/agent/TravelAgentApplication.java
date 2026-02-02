package com.travel.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Travel Agent Application - Main Entry Point
 * 
 * @author Travel Agent Team
 * @version 1.0
 */
@SpringBootApplication
@MapperScan("com.travel.agent.mapper")
public class TravelAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelAgentApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("⛴ Pathfinder Agent System Started!");
        System.out.println("========================================");
        System.out.println("📍 Server: http://localhost:8080");
        System.out.println("📍 API Base: http://localhost:8080/api");
        System.out.println("----------------------------------------");
        System.out.println("🧠 AI Architecture:");
        System.out.println("   • ReAct Agent: UnifiedReActAgent");
        System.out.println("   • LangGraph: Recommendation + Planning");
        System.out.println("   • LLM: OpenAI GPT-4o-mini + Gemini");
        System.out.println("----------------------------------------");
        System.out.println("💾 Data Layer:");
        System.out.println("   • Database: PostgreSQL (travel_agent)");
        System.out.println("   • Cache: Redis");
        System.out.println("========================================\n");
    }
}
