package com.bingewatch.catalog.service;

import com.bingewatch.catalog.repo.MovieRepository;
import com.bingewatch.catalog.domain.Movie;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CatalogService {
    private final MovieRepository repo;

    public CatalogService(MovieRepository repo) {
        this.repo = repo;
    }

    public List<Movie> findAll(String genre, Integer releaseYear){
        return repo.findAll(genre,releaseYear);
    }

    public Optional<Movie> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public Movie create(Movie movie) {
        return repo.save(movie);
    }

    @Transactional
    public void deleteById(Long id) {
        repo.deleteById(id);
    }
}
