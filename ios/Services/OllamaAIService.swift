import Foundation

enum AIError: Error, LocalizedError {
    case invalidURL
    case requestFailed(Error)
    case invalidResponse
    case decodingFailed(Error)
    case serverError(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "URL inválida"
        case .requestFailed(let error):
            return "Error de red: \(error.localizedDescription)"
        case .invalidResponse:
            return "Respuesta inválida del servidor"
        case .decodingFailed(let error):
            return "Error al decodificar: \(error.localizedDescription)"
        case .serverError(let message):
            return "Error del servidor: \(message)"
        }
    }
}

actor OllamaAIService {
    static let shared = OllamaAIService()

    private let baseURL = URL(string: "http://localhost:11434")!
    private let session = URLSession.shared
    private var model: String = "llama3.2"

    private init() {}

    func setModel(_ name: String) {
        model = name
    }

    func sendMessage(_ messages: [AIMessage]) async throws -> String {
        var ollamaMessages: [[String: String]] = []

        for msg in messages {
            ollamaMessages.append(["role": msg.role.rawValue, "content": msg.content])
        }

        let body: [String: Any] = [
            "model": model,
            "messages": ollamaMessages,
            "stream": false
        ]

        guard let url = URL(string: "\(baseURL.absoluteString)/api/chat") else {
            throw AIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw AIError.serverError("Respuesta no exitosa de Ollama")
        }

        struct OllamaResponse: Codable {
            struct Message: Codable {
                let role: String
                let content: String
            }
            let message: Message
            let done: Bool
        }

        do {
            let decoded = try JSONDecoder().decode(OllamaResponse.self, from: data)
            return decoded.message.content
        } catch {
            throw AIError.decodingFailed(error)
        }
    }
}
