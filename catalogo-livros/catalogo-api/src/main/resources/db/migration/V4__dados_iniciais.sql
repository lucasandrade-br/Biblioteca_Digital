INSERT INTO genres (name) VALUES
    ('Romance'),
    ('Ficção Científica'),
    ('Terror'),
    ('Policial'),
    ('Infantil'),
    ('Biografia e Memórias');

INSERT INTO authors (name, nationality) VALUES
    ('Machado de Assis', 'Brasileira'),
    ('Aluísio Azevedo', 'Brasileira'),
    ('José de Alencar', 'Brasileira'),
    ('Graciliano Ramos', 'Brasileira'),
    ('Jorge Amado', 'Brasileira'),
    ('Clarice Lispector', 'Brasileira'),
    ('João Guimarães Rosa', 'Brasileira'),
    ('Carolina Maria de Jesus', 'Brasileira'),
    ('George Orwell', 'Britânica'),
    ('Mary Shelley', 'Britânica'),
    ('Arthur Conan Doyle', 'Britânica'),
    ('Monteiro Lobato', 'Brasileira');

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Dom Casmurro', '9780000000019', 'Acervo da Biblioteca', 1899, 256, 'Bentinho, já velho, tenta reconstruir a própria juventude e a história com Capitu, deixando no leitor a dúvida sobre a traição que o atormenta.', id, 'ACTIVE', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Romance';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Memórias Póstumas de Brás Cubas', '9780000000026', 'Acervo da Biblioteca', 1881, 240, 'Um defunto autor narra a própria vida com ironia, expondo as vaidades da elite do Rio de Janeiro do século XIX.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Romance';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'O Cortiço', '9780000000033', 'Acervo da Biblioteca', 1890, 304, 'A vida coletiva de uma habitação popular do Rio de Janeiro, movida pela ambição do português João Romão.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Romance';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Iracema', '9780000000040', 'Acervo da Biblioteca', 1865, 144, 'O encontro entre a indígena Iracema e o colonizador Martim, em uma lenda sobre as origens do Ceará.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Romance';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Vidas Secas', '9780000000057', 'Acervo da Biblioteca', 1938, 176, 'Fabiano, Sinhá Vitória, os dois meninos e a cachorra Baleia enfrentam a seca e a injustiça no sertão nordestino.', id, 'ACTIVE', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Romance';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Capitães da Areia', '9780000000064', 'Acervo da Biblioteca', 1937, 288, 'O cotidiano de um grupo de meninos abandonados que vivem de pequenos furtos nas ruas de Salvador.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Romance';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'A Hora da Estrela', '9780000000071', 'Acervo da Biblioteca', 1977, 88, 'A história de Macabéa, jovem alagoana no Rio de Janeiro, contada por um narrador que questiona o próprio ato de escrever.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Romance';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Grande Sertão: Veredas', '9780000000088', 'Acervo da Biblioteca', 1956, 624, 'O ex-jagunço Riobaldo relata suas travessias pelo sertão, seu amor por Diadorim e o suposto pacto com o diabo.', id, 'ACTIVE', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Romance';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Quarto de Despejo', '9780000000095', 'Acervo da Biblioteca', 1960, 200, 'O diário de uma catadora de papel da favela do Canindé, em São Paulo, sobre a fome e o cotidiano da pobreza.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Biografia e Memórias';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT '1984', '9780000000101', 'Acervo da Biblioteca', 1949, 416, 'Winston Smith tenta preservar a própria consciência em um Estado totalitário que vigia e reescreve a história.', id, 'ACTIVE', 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Ficção Científica';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'A Revolução dos Bichos', '9780000000118', 'Acervo da Biblioteca', 1945, 152, 'Os animais de uma granja expulsam o dono, mas veem o novo governo repetir os abusos que combateram.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Ficção Científica';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Frankenstein', '9780000000125', 'Acervo da Biblioteca', 1818, 280, 'O jovem cientista Victor Frankenstein dá vida a uma criatura e passa a ser perseguido pelas consequências de seu ato.', id, 'ACTIVE', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Terror';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Um Estudo em Vermelho', '9780000000132', 'Acervo da Biblioteca', 1887, 176, 'A primeira investigação de Sherlock Holmes e do doutor Watson, a partir de um crime misterioso em Londres.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Policial';

INSERT INTO books (titulo, isbn, publisher, publication_year, numero_paginas, synopsis, genre_id, status, total_copies, loaned_copies, created_at, updated_at)
SELECT 'Reinações de Narizinho', '9780000000149', 'Acervo da Biblioteca', 1931, 272, 'As aventuras de Narizinho, Pedrinho e da boneca Emília no Sítio do Picapau Amarelo.', id, 'ACTIVE', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM genres WHERE name = 'Infantil';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Dom Casmurro' AND a.name = 'Machado de Assis';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Memórias Póstumas de Brás Cubas' AND a.name = 'Machado de Assis';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'O Cortiço' AND a.name = 'Aluísio Azevedo';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Iracema' AND a.name = 'José de Alencar';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Vidas Secas' AND a.name = 'Graciliano Ramos';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Capitães da Areia' AND a.name = 'Jorge Amado';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'A Hora da Estrela' AND a.name = 'Clarice Lispector';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Grande Sertão: Veredas' AND a.name = 'João Guimarães Rosa';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Quarto de Despejo' AND a.name = 'Carolina Maria de Jesus';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = '1984' AND a.name = 'George Orwell';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'A Revolução dos Bichos' AND a.name = 'George Orwell';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Frankenstein' AND a.name = 'Mary Shelley';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Um Estudo em Vermelho' AND a.name = 'Arthur Conan Doyle';

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id FROM books b, authors a WHERE b.titulo = 'Reinações de Narizinho' AND a.name = 'Monteiro Lobato';
