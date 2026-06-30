package com.bingewatch.catalog.service;

import com.bingewatch.catalog.exception.NotFoundException;
import com.bingewatch.catalog.repo.MovieRepository;
import com.bingewatch.catalog.domain.Movie;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CatalogService {
    private final MovieRepository repo;

    public CatalogService(MovieRepository repo) {
        this.repo = repo;
    }

    public List<Movie> findAll(String genre, Integer releaseYear){
        return repo.findAll(genre,releaseYear);
    }

    public Movie findById(Long id) {

        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Movie not found with id: "+id));
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
