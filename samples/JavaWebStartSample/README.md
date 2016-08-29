# Java Web Start Sample

このプロジェクトは、データセットにアプリとして登録するプログラムのサンプルです。

## ビルド方法

### assembled jar ファイルの作成

以下のコマンドを実行すると target/scala-2.11/ 以下に
dsmoq-jws-sample-assembly-1.0.0.jar ファイルが作成されます。

```
sbt assemble
```

### 署名の付与

Java Web Start のアプリケーションとして登録するためには、
作成されたjarファイルに対して署名を付与する必要があります。

署名を付与しないとユーザ実行時にエラーとなります。

#### 署名付与コマンド例
```
keytool -genkey -keyalg rsa -alias sample
jarsigne dsmoq-jws-sample-assembly-1.0.0.jar sample
```
