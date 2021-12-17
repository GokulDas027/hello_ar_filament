# hello_ar_filament
Hello World to using ArCore with Filament.

It detects plane, place a single object(bundled along as asset) on tap and transform it. Uses Depth mode if available for device.

<div>
  <img src="https://user-images.githubusercontent.com/22936485/146503755-3f6c9fd6-a0fd-42eb-a3ec-35b6bdad73d5.jpg" alt="hello_ar_filament_1" width="300"/>
  <img src="https://user-images.githubusercontent.com/22936485/146503765-14e63a5e-245d-4fa4-9b58-5c93c4a4f683.jpg" alt="hello_ar_filament_2" width="300"/>
</div>

## Important
* This project also includes Filament's material builder gradle plugin, which compiles `.matc` materials in `src/main/materials` to `.filamat` in `src/main/assets/materials`. Compiling is based on `matc` in `filament/bin`, acquired from filament release. 
  > It's required to update the `matc` as you update the filament version.
* Can also user `build-materials.sh` to recompile materials manually.  

know more about filament [here](https://github.com/google/filament)

Build with reusability in mind.

## Thanks to

Filament [Samples](https://github.com/google/filament/tree/main/android/samples)

ArCore [Samples](https://github.com/google-ar/arcore-android-sdk/tree/master/samples)

Reference [zirman/arcore-filament-example-app](https://github.com/zirman/arcore-filament-example-app)

glTF Model ["Perry" by Sprint](https://skfb.ly/6Rrry).
