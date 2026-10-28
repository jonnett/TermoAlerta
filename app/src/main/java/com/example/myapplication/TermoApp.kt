package com.example.myapplication

import android.app.Application

/** Punto de arranque: inicializa ajustes, base de datos, notificaciones y controladores. */
class TermoApp : Application() {

    lateinit var database: ReadingsDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        AuthRepository.init(this)
        database = ReadingsDatabase(this)
        AlertNotifier.createChannels(this)
        MonitorController.init(this, database)
        SensorController.init()
    }
}
