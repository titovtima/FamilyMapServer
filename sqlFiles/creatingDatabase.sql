create table users
(
    id       serial primary key,
    login    varchar(128) unique not null,
    password varchar(128)        not null,
    name     varchar(128)        not null
);

create table user_location
(
    user_id   int    not null references users on delete cascade,
    latitude  int    not null,
    longitude int    not null,
    date      bigint not null,
    primary key (user_id, date)
);

create table user_share_location
(
    user_sharing_id   int references users on delete cascade,
    user_shared_to_id int references users on delete cascade,
    primary key (user_sharing_id, user_shared_to_id)
);

create table user_ask_for_sharing_location
(
    user_asking_id    int references users on delete cascade,
    user_asked_for_id int references users on delete cascade,
    primary key (user_asking_id, user_asked_for_id)
);

create table user_saved_contacts
(
    id             serial primary key,
    owner_id       int          not null references users on delete cascade,
    user_ref_to_id int          references users on delete set null,
    name           varchar(128) not null,
    show_location  bool         not null default true,
    unique (owner_id, user_ref_to_id)
);
