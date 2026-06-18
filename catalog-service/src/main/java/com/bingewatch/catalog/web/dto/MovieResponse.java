package com.bingewatch.catalog.web.dto;

import com.bingewatch.catalog.domain.Movie;

public record MovieResponse(Long id, String title, String genre, Integer releaseYear) {
    public static MovieResponse from(Movie movie){
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getGenre(),
                movie.getReleaseYear());
    }
}
