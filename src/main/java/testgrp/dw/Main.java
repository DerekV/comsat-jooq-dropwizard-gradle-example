package testgrp.dw;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.dropwizard.FiberApplication;
import co.paralleluniverse.fibers.jdbc.FiberDataSource;
import com.codahale.metrics.*;
import com.codahale.metrics.annotation.*;
import com.fasterxml.jackson.annotation.*;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.setup.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.RecordMapper;

import static org.jooq.impl.DSL.*;

public final class Main extends FiberApplication<Main.JModernConfiguration> {
    public static void main(String[] args) throws Exception {
        new Main().run("server", System.getProperty("dropwizard.config"));
    }

    @Override
    public final void fiberRun(JModernConfiguration cfg, Environment env) throws ClassNotFoundException, SQLException {
        JmxReporter.forRegistry(env.metrics()).build().start(); // JMX reporting
        final DataSource ds = cfg.getDataSourceFactory().build(env.metrics(), "db"); // Dropwizard will monitor the connection pool
        initDB(ds);
        env.jersey().register(new DBResource(FiberDataSource.wrap(ds)));
    }

    private void initDB(DataSource ds) throws SQLException {
        try (final Connection conn = ds.getConnection()) {
            conn.createStatement().execute("create table something (id int primary key auto_increment, name varchar(100))");

            final String[] names = {"Gigantic", "Bone Machine", "Hey", "Cactus"};
            final DSLContext context = using(conn);
            for (final String name : names)
                context.insertInto(table("something"), field("name")).values(name).execute();
        }
    }

    // YAML Configuration
    static class JModernConfiguration extends io.dropwizard.Configuration {
        @Valid @NotNull @JsonProperty private DataSourceFactory database = new DataSourceFactory();

        DataSourceFactory getDataSourceFactory() { return database; }
    }

    @Path("/db")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class DBResource {
        private final DataSource ds;
        private static final RecordMapper<Record, Something> toSomething =
                record ->
                    new Something (
                        record.getValue(field("id", Integer.class)),
                        record.getValue(field("name", String.class))
                    );

        @SuppressWarnings("WeakerAccess")
        public DBResource(DataSource ds) throws SQLException {
            this.ds = ds;
        }

        @Timed
        @POST @Path("/add")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public final Something add(@FormParam("name") String name) throws SQLException, SuspendExecution {
            try (final Connection conn = ds.getConnection()) {
                using(conn).insertInto(table("something"), field("name")).values(name).execute();
            }
            return findByName(name);
        }

        @Timed
        @POST @Path("/update")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public final Something update(@FormParam("name") String name, @FormParam("id") Integer id) throws SQLException, SuspendExecution {
            try (final Connection conn = ds.getConnection()) {
                using(conn).update(table("something")).set(field("name"), name).where(field("id", Integer.class).eq(id)).execute();
            }
            return findById(id);
        }

        @Timed
        @POST @Path("/delete")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public final Something delete(@FormParam("id") Integer id) throws SQLException, SuspendExecution {
            final Something ret = findById(id);
            try (final Connection conn = ds.getConnection()) {
                using(conn).delete(table("something")).where(field("id", Integer.class).eq(id)).execute();
            }
            return ret;
        }

        @Timed
        @GET @Path("/item/byId/{id}")
        public final Something findById(@PathParam("id") Integer id) throws SQLException, SuspendExecution {
            try (final Connection conn = ds.getConnection()) {
                return using(conn).select(field("id"), field("name")).from(table("something"))
                        .where(field("id", Integer.class).equal(id)).fetchOne().map(toSomething);
            }
        }

        @Timed
        @GET @Path("/item/byName/{name}")
        public final Something findByName(@PathParam("name") String name) throws SQLException, SuspendExecution {
            try (final Connection conn = ds.getConnection()) {
                return using(conn).select(field("id"), field("name")).from(table("something"))
                        .where(field("name", String.class).equal(name)).fetchOne().map(toSomething);
            }
        }

        @Timed
        @GET @Path("/all")
        public final List<Something> all() throws SQLException, SuspendExecution {
            try (final Connection conn = ds.getConnection()) {
                return using(conn).select(field("id"), field("name")).from(table("something")).fetch().map(toSomething);
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class Something {
        @JsonProperty public final int id;
        @JsonProperty public final String name;

        public Something(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
