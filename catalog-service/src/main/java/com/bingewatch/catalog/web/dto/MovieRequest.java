package com.bingewatch.catalog.web.dto;

import com.bingewatch.catalog.domain.Movie;

public record MovieRequest(String title, String genre, Integer releaseYear){
    public Movie toEntity(){
        return new Movie(title, genre, releaseYear);
    }
}
