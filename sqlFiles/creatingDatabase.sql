create table "User" (
                        id serial primary key,
                        login varchar(128) unique not null,
                        password varchar(128) not null,
                        name varchar(128) not null
);

create table UserLocation(
                             userId int not null,
                             latitude int not null,
                             longitude int not null,
                             date bigint not null,
                             primary key (userId, date),
                             foreign key (userId) references "User" (id)
                                 on delete cascade
);

create table UserShareLocation(
                                  userSharingId int not null,
                                  userSharedToId int not null,
                                  primary key (userSharingId, userSharedToId),
                                  foreign key (userSharingId) references "User" (id)
                                      on delete cascade,
                                  foreign key (userSharedToId) references "User" (id)
                                      on delete cascade
);

create table UserAskForShareLocation(
                                        userAskingId int not null,
                                        userAskedForId int not null,
                                        primary key (userAskingId, userAskedForId),
                                        foreign key (userAskingId) references "User" (id)
                                            on delete cascade,
                                        foreign key (userAskedForId) references "User" (id)
                                            on delete cascade
);

create table UserSavedContacts(
                                  contactId serial primary key,
                                  userId int not null,
                                  contactUserId int,
                                  name varchar(128) not null,
                                  showLocation bool default true,
                                  unique (userId, contactUserId),
                                  foreign key (userId) references  "User" (id)
                                      on delete cascade,
                                  foreign key (contactUserId) references "User" (id)
                                      on delete set null
);
