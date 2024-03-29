create schema if not exists SVANNA;
drop table if exists SVANNA.TAD_BOUNDARY;
create table SVANNA.TAD_BOUNDARY
(
    CONTIG    INT          not null,
    START     INT          not null, -- zero-based start on POSITIVE strand
    END       INT          not null, -- zero-based end on POSITIVE strand
    MIDPOINT  INT          not null,
    ID        VARCHAR(200) not null,
    STABILITY FLOAT        not null
);
create index SVANNA.TAD_BOUNDARY__CONTIG_START_END_IDX
    on SVANNA.TAD_BOUNDARY (CONTIG, START, END);
create index SVANNA.TAD_BOUNDARY__CONTIG_MIDPOINT_IDX
    on SVANNA.TAD_BOUNDARY (CONTIG, MIDPOINT);
