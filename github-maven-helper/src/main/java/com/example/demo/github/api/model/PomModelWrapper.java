package com.example.demo.github.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.maven.model.Model;
import org.kohsuke.github.GHRepository;

@Data
@AllArgsConstructor
public class PomModelWrapper {
    private Model pomModel;
}
