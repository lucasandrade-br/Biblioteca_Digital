package org.example.catalogo.model;

import org.example.catalogo.model.enums.BookStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(nullable = false, length = 120)
    private String publisher;

    @Column(name = "publication_year", nullable = false)
    private int publicationYear;

    @Column(name = "numero_paginas", nullable = false)
    private int numeroPaginas;

    @Column(nullable = false, length = 2000)
    private String synopsis;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @ManyToMany
    @JoinTable(
        name = "book_authors",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    @OrderBy("name ASC")
    private List<Author> authors = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookStatus status;

    @Column(name = "total_copies", nullable = false)
    private int totalCopies;

    @Column(name = "loaned_copies", nullable = false)
    private int loanedCopies;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Book() {
    }

    public Book(
        String titulo,
        String isbn,
        String publisher,
        int publicationYear,
        int numeroPaginas,
        String synopsis,
        String coverUrl,
        Genre genre,
        Collection<Author> authors,
        int totalCopies,
        Instant now
    ) {
        this.titulo = titulo;
        this.isbn = isbn;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.numeroPaginas = numeroPaginas;
        this.synopsis = synopsis;
        this.coverUrl = coverUrl;
        this.genre = genre;
        this.authors.addAll(authors);
        this.totalCopies = totalCopies;
        this.loanedCopies = 0;
        this.status = BookStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
        String titulo,
        String isbn,
        String publisher,
        int publicationYear,
        int numeroPaginas,
        String synopsis,
        String coverUrl,
        Genre genre,
        Collection<Author> authors,
        int totalCopies,
        Instant now
    ) {
        if (totalCopies < loanedCopies) {
            throw new IllegalArgumentException("Total de exemplares não pode ser menor que os " + loanedCopies + " em circulação");
        }
        this.titulo = titulo;
        this.isbn = isbn;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.numeroPaginas = numeroPaginas;
        this.synopsis = synopsis;
        this.coverUrl = coverUrl;
        this.genre = genre;
        this.authors.clear();
        this.authors.addAll(authors);
        this.totalCopies = totalCopies;
        this.updatedAt = now;
    }

    public void reserveCopy() {
        if (getAvailableCopies() <= 0) {
            throw new IllegalArgumentException("Não há exemplares disponíveis deste livro");
        }
        loanedCopies++;
    }

    public void releaseCopy() {
        if (loanedCopies <= 0) {
            throw new IllegalStateException("Não há exemplares em circulação para liberar");
        }
        loanedCopies--;
    }

    public void deactivate(Instant now) {
        status = BookStatus.INACTIVE;
        updatedAt = now;
    }

    public void activate(Instant now) {
        status = BookStatus.ACTIVE;
        updatedAt = now;
    }

    public int getAvailableCopies() {
        return totalCopies - loanedCopies;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getPublisher() {
        return publisher;
    }

    public int getPublicationYear() {
        return publicationYear;
    }

    public int getNumeroPaginas() {
        return numeroPaginas;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public Genre getGenre() {
        return genre;
    }

    public List<Author> getAuthors() {
        return Collections.unmodifiableList(authors);
    }

    public BookStatus getStatus() {
        return status;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public int getLoanedCopies() {
        return loanedCopies;
    }
}
