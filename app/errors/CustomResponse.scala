package errors

case class CustomResponse(responseCode: Int, statusMessage: String)

object CustomResponse{

  def customResponse(responseCode: Int): CustomResponse = responseCode match {
    case 401 => CustomResponse(401, "failed authorization")
  }
}
