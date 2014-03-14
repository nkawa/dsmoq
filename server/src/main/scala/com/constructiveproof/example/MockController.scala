package com.constructiveproof.example

import org.scalatra.ScalatraServlet

class MockController extends ScalatraServlet {
  get("/") {
    <html>
      <body>
        <h1>File Upload Test</h1>
        <h2>Login ※NOT Ajax</h2>
        <form action="../api/signin" method="post">
          <input type="text" name="id" value="test" />
          <input type="text" name="password" value="foo" />
          <input type="submit" value="Login" />
        </form>

        <h2>File Upload</h2>
        <form action="../api/datasets" method="post" enctype="multipart/form-data">
          <p>File to upload: <input type="file" name="file[]" /></p>
          <p>File to upload: <input type="file" name="file[]" /></p>
          <p><input type="submit" value="Upload" /></p>
        </form>
      </body>
    </html>
  }
}
