package com.bingewatch.catalog.web.dto;

import com.bingewatch.catalog.domain.Movie;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MovieRequest(
        @NotBlank String title,
        @NotBlank String genre,
        @NotNull @Min(1888) Integer releaseYear){
    
    public Movie toEntity(){
        return new Movie(title, genre, releaseYear);
    }
}
