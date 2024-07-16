/* Tabella fedex cofnfiguration, capire se ci va messa anche la parte sotto (che viene fatta nel .groovy)*/
create table fedex_configuration
(
    id                   int          not null
        primary key,
    code                 varchar(255) null,
    default_label_format varchar(255) null,
    is_active            bit          null,
    name                 varchar(255) null,
    order_id_in_notes    bit          null,
    shipper_default      bit          null,
    shipper_type         int          null,
    store_id             bigint       not null,
    title                varchar(255) null,
    virtual_shipper_type int          null,
    client_code          varchar(255) null,
    password             varchar(255) null,
    username             varchar(255) null,
    departure_depot      varchar(255) null,
    default_tariff       varchar(255) null,
    product_types        varchar(255) null
);

create index store_idx
    on fedex_configuration (store_id);