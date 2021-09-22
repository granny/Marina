package me.scarsz.marina;

import com.mongodb.MongoClient;
import lombok.Getter;
import me.scarsz.jdaappender.ChannelLoggingHandler;
import me.scarsz.marina.feature.AbstractFeature;
import me.scarsz.marina.feature.DevelopmentFeature;
import me.scarsz.marina.feature.docker.DockerFeature;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.apache.commons.lang.StringUtils;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.util.HashMap;
import java.util.Map;

public class Marina {

    @Getter private static Marina instance;
    @Getter private static final Logger logger = LoggerFactory.getLogger(Marina.class);

    @Getter private final Jongo datastore;
    @Getter private final MongoCollection usersCollection;
    @Getter private final JDA jda;
    @Getter private final Map<Class<? extends AbstractFeature>, AbstractFeature> features = new HashMap<>();

    public Marina() throws LoginException, InterruptedException {
        Marina.instance = this;

        this.datastore = new Jongo(new MongoClient("mongo:27017").getDB("marina"));
        this.usersCollection = this.datastore.getCollection("users");

        this.jda = JDABuilder.createDefault(System.getenv("TOKEN"))
                .setActivity(
                        StringUtils.isNotBlank(System.getenv("WATCHING")) ?
                                Activity.watching(System.getenv("WATCHING"))
                        : StringUtils.isNotBlank(System.getenv("PLAYING")) ?
                                Activity.playing(System.getenv("PLAYING"))
                        : Activity.watching("🚢 ⛵")
                )
                .build();
        if (StringUtils.isNotBlank(System.getenv("LOGGING_CHANNEL"))) {
            new ChannelLoggingHandler(() -> this.jda.getTextChannelById(System.getenv("LOGGING_CHANNEL")), c -> {
                c.mapLoggerName("com.github.dockerjava", "Docker");
                c.mapLoggerName("net.dv8tion.jda", "JDA");
                c.mapLoggerName("org.mongodb", "MongoDB");
                c.mapLoggerNameFriendly("me.scarsz.marina.feature", name -> name.replace("Feature", " Feature"));
                c.mapLoggerNameFriendly("me.scarsz.marina", name -> "Marina > " + name);
                c.addFilter(logItem -> logItem.getMessage().contains("dockerHttpClient")); // useless warning
            }).attachJavaLogging().schedule();
        }
        this.jda.awaitReady();

        // features
        new Permissions();
        new DevelopmentFeature();
        new DockerFeature();
//        new TagsFeature();
    }

    public static <F extends AbstractFeature> F getFeature(Class<F> clazz) {
        //noinspection unchecked
        return (F) Marina.instance.getFeatures().get(clazz);
    }

}
