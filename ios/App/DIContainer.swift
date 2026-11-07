import Foundation

@MainActor
final class ContenedorServicios {
    static let shared = ContenedorServicios()

    private init() {}

    private lazy var _autenticacion = ServicioAutenticacion()
    private lazy var _documentos = ServicioDocumentos()
    private lazy var _expedientes = ServicioExpedientes()
    private lazy var _archivo = ServicioArchivo()
    private lazy var _notificaciones = ServicioNotificaciones()
    private lazy var _configuracion = ServicioConfiguracion()
    private lazy var _qr = ServicioQR()
    private lazy var _biometrico = ServicioAutenticacionBiometrica()

    func autenticacion() -> ServicioAutenticacion { _autenticacion }
    func documentos() -> ServicioDocumentos { _documentos }
    func expedientes() -> ServicioExpedientes { _expedientes }
    func archivo() -> ServicioArchivo { _archivo }
    func notificaciones() -> ServicioNotificaciones { _notificaciones }
    func configuracion() -> ServicioConfiguracion { _configuracion }
    func qr() -> ServicioQR { _qr }
    func biometrico() -> ServicioAutenticacionBiometrica { _biometrico }
}
