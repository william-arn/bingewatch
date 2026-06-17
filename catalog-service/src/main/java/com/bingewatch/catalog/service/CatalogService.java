package com.bingewatch.catalog.service;

import com.bingewatch.catalog.repo.MovieRepository;
import com.bingewatch.catalog.domain.Movie;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CatalogService {
    private final MovieRepository repo;

    public CatalogService(MovieRepository repo) {
        this.repo = repo;
    }

    public List<Movie> findAll(String genre) {
        return (genre == null) ? repo.findAll() : repo.findByGenre(genre);
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
