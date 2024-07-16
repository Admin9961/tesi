/** Creazione della tabella fedex_product_type */
create table fedex_product_type
(
    id          bigint auto_increment
        primary key,
    version     bigint       null,
    code        varchar(255) null,
    description varchar(255) null,
    location    varchar(255) null
);

/** Creazione della tabella fedex_booking */
create table fedex_booking
(
    id               bigint auto_increment
        primary key,
    version          bigint       not null,
    availabilitytime datetime     null,
    pickupdate       datetime     null,
    pickupinstr      varchar(255) null,
    pickuptime       datetime     null,
    priclotime       datetime     null,
    priopntime       datetime     null,
    secclotime       datetime     null,
    secopntime       datetime     null
);

/** booking_id: foreign key che identifica l'id nella tabella fedex_booking */
alter table shipping
    add fedex_booking_id bigint null;

/** creazione della foreign key che punta alla tabella fedex_booking.id */
alter table shipping
    add constraint shipping_fedex_booking_id_fk
        foreign key (fedex_booking_id) references fedex_booking (id);

/** product_type_id: foreign key che identifica l'id nella tabella fedex_product_type */
alter table shipping
    add fedex_product_type_id bigint null;

/** creazione della foreign key che punta alla tabella fedex_product_type.id */
alter table shipping
    add constraint shipping_fedex_product_type_id_fk
        foreign key (fedex_product_type_id) references fedex_product_type (id);