package com.travel.agent.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

public class MyCodeGenerator {
    public static void main(String[] args) {
        // 数据库连接配置
        String url = "jdbc:mysql://localhost:3306/travel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Australia/Sydney";
        String username = "root";
        String password = "root"; // 请修改为你的数据库密码

        // 获取项目根目录(假设运行在 backend 模块)
        String projectPath = System.getProperty("user.dir");
        
        FastAutoGenerator.create(url, username, password)
            .globalConfig(builder -> {
                builder.author("Seren")
                       .outputDir(projectPath + "/src/main/java")  // 输出到 backend/src/main/java
                       .disableOpenDir()                            // 生成后不自动打开文件夹
                       .enableSwagger();                            // 启用 Swagger 注解
            })
            .packageConfig(builder -> {
                builder.parent("com.travel.agent")                  // 父包名
                       .entity("entity")                            // Entity 包名
                       .mapper("mapper")                            // Mapper 包名
                       .service("service")                          // Service 包名
                       .serviceImpl("service.impl")                 // ServiceImpl 包名
                       .controller("controller")                    // Controller 包名
                       .pathInfo(Collections.singletonMap(
                           OutputFile.xml, 
                           projectPath + "/src/main/resources/mapper"  // Mapper XML 输出到 resources/mapper
                       ));
            })
            .strategyConfig(builder -> {
                // 实体类策略配置
                builder.entityBuilder()
                       .enableLombok();                              // 启用 Lombok
                
                // Mapper 策略配置
                builder.mapperBuilder()
                       .enableBaseResultMap()                       // 生成 BaseResultMap
                       .enableBaseColumnList();                     // 生成 BaseColumnList
                
                // Service 策略配置
                builder.serviceBuilder()
                       .formatServiceFileName("%sService")          // Service 接口命名格式
                       .formatServiceImplFileName("%sServiceImpl"); // ServiceImpl 命名格式
                
                // Controller 策略配置
                builder.controllerBuilder()
                       .enableRestStyle()                           // 启用 REST 风格
                       .enableHyphenStyle();                        // 启用驼峰转连字符
                
                // 指定要生成的表(Phase 3 需要的表)
                builder.addInclude(
                    "trips",                    // 行程表
                    "itinerary_days",           // 每日行程表
                    "itinerary_items",          // 行程活动明细表
                    "itinerary_versions"        // 行程版本历史表（可选）
                );
                
                // 过滤表前缀(如果有的话)
                builder.addTablePrefix("");
            })
            .templateEngine(new VelocityTemplateEngine())           // 使用 Velocity 模板引擎
            .execute();                                             // 执行生成
    }
}