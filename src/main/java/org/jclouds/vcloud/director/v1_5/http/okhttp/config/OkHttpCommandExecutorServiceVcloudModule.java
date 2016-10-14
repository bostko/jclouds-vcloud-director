package org.jclouds.vcloud.director.v1_5.http.okhttp.config;

import com.google.inject.Scopes;
import org.jclouds.http.HttpCommandExecutorService;
import org.jclouds.http.config.ConfiguresHttpCommandExecutorService;
import org.jclouds.http.okhttp.config.OkHttpCommandExecutorServiceModule;
import org.jclouds.http.internal.OkHttpCommandExecutorVcloudService;

@ConfiguresHttpCommandExecutorService
public class OkHttpCommandExecutorServiceVcloudModule extends OkHttpCommandExecutorServiceModule {
    @Override
    protected void configure() {
        super.configure();
        bind(HttpCommandExecutorService.class).to(OkHttpCommandExecutorVcloudService.class).in(Scopes.SINGLETON);
    }
}
