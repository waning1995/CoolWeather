package com.aning.coolweather

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.view.GravityCompat
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aning.coolweather.db.City
import com.aning.coolweather.db.County
import com.aning.coolweather.db.Province
import com.aning.coolweather.gson.Weather
import com.aning.coolweather.service.AutoUpdateWeatherService
import com.aning.coolweather.util.HttpUtil
import com.aning.coolweather.util.Utility
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.activity_weather.*
import kotlinx.android.synthetic.main.now.*
import kotlinx.android.synthetic.main.title.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.litepal.LitePal
import java.io.IOException

class WeatherActivity : AppCompatActivity() {


    private lateinit var _weatherLayout: ScrollView;
    private lateinit var _titleCity: TextView;
    private lateinit var _titleUpdateTime: TextView;
    private lateinit var _degreeText: TextView;
    private lateinit var _weatherInfoText: TextView;
    private lateinit var _forecastLayout: LinearLayout;
    private lateinit var _aqiText: TextView;
    private lateinit var _pm25Text: TextView;
    private lateinit var _comfortText: TextView;
    private lateinit var _carWashText: TextView;
    private lateinit var _sportText: TextView;

    private lateinit var _weatherId: String;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 21) {
            val decorView = this.window.decorView;
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            this.window.statusBarColor = Color.TRANSPARENT;
        }
        setContentView(R.layout.activity_weather);

        this._weatherLayout = this.findViewById(R.id.weather_layout);
        this._titleCity = this.findViewById(R.id.title_city);
        this._titleUpdateTime = this.findViewById(R.id.title_update_time);
        this._degreeText = this.findViewById(R.id.degree_text);
        this._weatherInfoText = this.findViewById(R.id.weather_info_text);
        this._forecastLayout = this.findViewById(R.id.forecast_layout);
        this._aqiText = this.findViewById(R.id.aqi_text);
        this._pm25Text = this.findViewById(R.id.pm25_text);
        this._comfortText = this.findViewById(R.id.comfort_text);
        this._carWashText = this.findViewById(R.id.car_wash_text);
        this._sportText = this.findViewById(R.id.sport_text);

        this.swipe_refresh.setColorSchemeResources(R.color.colorPrimary);
        val prefs = PreferenceManager.getDefaultSharedPreferences(this);
        val weatherString = prefs.getString("weather", null);

        if (weatherString != null) {
            //已经有缓存
            val weather = Utility.handleWeatherResponse(weatherString);
            this._weatherId = weather.basic.weatherId;
            this.showWeatherInfo(weather);
        } else {
            //没有缓存, 前往服务器查询
            this._weatherId = this.intent.getStringExtra("weather_id");
            this._weatherLayout.visibility = View.INVISIBLE;
            this.requestWeather(this._weatherId);
        }

        this.swipe_refresh.setOnRefreshListener {
            requestWeather(this._weatherId);
        }

        this.nav_button.setOnClickListener {
            this.drawer_layout.openDrawer(GravityCompat.START);
        }

        val bingPic = prefs.getString("bing_pic", null);
        if (bingPic != null) {
            Glide.with(this)
                    .load(bingPic)
                    .into(this.bing_pic_img);
        } else {
            loadBingPic();
        }
    }

    /**
     * 切换城市
     * @param weatherId 指定要切换的天气 id
     */
    public fun switchCounty(weatherId: String) {
        this.drawer_layout.closeDrawers();
        if (this._weatherId != weatherId) {
            this._weatherId = weatherId;
            this.swipe_refresh.isRefreshing = true;
            this.requestWeather(weatherId);
        }
    }

    /**
     * 根据天气 id 请求城市天气信息
     */
    private fun requestWeather(weatherId: String) {
        val weatherUrl = "http://guolin.tech/api/weather?cityid=$weatherId&key=24fc4a5349284cefb252760c6d26cceb";
        HttpUtil.sendOkHttpRequest(weatherUrl, object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body()?.string();
                if (responseText.isNullOrEmpty())
                    return;
                else {
                    val weather = Utility.handleWeatherResponse(responseText!!);
                    runOnUiThread {
                        if (weather.status == "ok") {
                            val editor = PreferenceManager.getDefaultSharedPreferences(this@WeatherActivity)
                                    .edit();
                            editor.putString("weather", responseText);
                            editor.apply();
                            showWeatherInfo(weather);
                        } else {
                            Toast.makeText(this@WeatherActivity, "获取天气数据失败", Toast.LENGTH_SHORT)
                                    .show();
                        }
                        this@WeatherActivity.swipe_refresh.isRefreshing = false;
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace();
                runOnUiThread {
                    Toast.makeText(this@WeatherActivity, "获取天气数据失败", Toast.LENGTH_SHORT)
                            .show();
                    this@WeatherActivity.swipe_refresh.isRefreshing = false;
                }
            }
        });

        loadBingPic();
    }

    /**
     * 处理并显示 [Weather] 实例中的数据
     */
    private fun showWeatherInfo(weather: Weather) {
        val intent = Intent(this, AutoUpdateWeatherService::class.java);
        this.startService(intent);
        val cityName = weather.basic.cityName;
        val updateTime = weather.basic.update.updateTime.split(" ")[1];
        val degree = "${weather.now.temperature}℃";
        val weatherInfo = weather.now.more.info;

        this._titleCity.text = cityName;
        this._titleUpdateTime.text = updateTime;
        this._degreeText.text = degree;
        this._weatherInfoText.text = weatherInfo;
        this._forecastLayout.removeAllViews();
        for (forecast in weather.forecastList) {
            val view = LayoutInflater.from(this)
                    .inflate(R.layout.forecast_item, this._forecastLayout, false);
            view.findViewById<TextView>(R.id.date_text).text = forecast.date;
            view.findViewById<TextView>(R.id.info_text).text = forecast.more.info;
            view.findViewById<TextView>(R.id.max_text).text = forecast.temperature.max;
            view.findViewById<TextView>(R.id.min_text).text = forecast.temperature.min;
            this._forecastLayout.addView(view);
        }

        this._aqiText.text = weather.aqi.city.aqi;
        this._pm25Text.text = weather.aqi.city.pm25;

        this._comfortText.text = "舒适度: ${weather.suggestion.comfort.info}";
        this._carWashText.text = "洗车指数: ${weather.suggestion.carWash.info}";
        this._sportText.text = "运动建议: ${weather.suggestion.sport.info}";
        this._weatherLayout.visibility = View.VISIBLE;
    }

    /**
     * 加载必应每日一图
     */
    private fun loadBingPic() {
        val requestBingPic = "http://guolin.tech/api/bing_pic";
        HttpUtil.sendOkHttpRequest(requestBingPic, object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val bingPic = response.body()?.string();
                val editor = PreferenceManager.getDefaultSharedPreferences(this@WeatherActivity)
                        .edit();
                editor.putString("bing_pic", bingPic);
                editor.apply();
                runOnUiThread {
                    Glide.with(this@WeatherActivity)
                            .load(bingPic)
                            .into(this@WeatherActivity.bing_pic_img);
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace();
            }
        })
    }
}
