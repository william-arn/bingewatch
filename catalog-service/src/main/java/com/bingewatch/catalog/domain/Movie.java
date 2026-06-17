package com.bingewatch.catalog.domain;

import jakarta.persistence.*;

@Entity
@Table(name="movies")
public class Movie{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String genre;
    @Column(name = "release_year")
    private Integer releaseYear;

    protected Movie(){}

    public Movie(String title, String genre, Integer releaseYear) {
        this.title = title;
        this.genre = genre;
        this.releaseYear = releaseYear;
    }

    public Long getId(){return id;}
    public String getTitle(){return title;}
    public void setTitle(String title){this.title = title;}
    public String getGenre(){return genre;}
    public void setGenre(String genre){this.genre = genre;}
    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }

}