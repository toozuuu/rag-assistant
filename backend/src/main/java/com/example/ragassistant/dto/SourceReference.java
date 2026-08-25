package com.example.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {
    private String document;
    private String section;
    private String snippet;
    private Integer pageNumber;
    private String filePath;
    private String language;
    private String repository;
    private String branch;
}

