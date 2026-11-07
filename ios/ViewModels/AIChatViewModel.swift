
import SwiftUI

@MainActor
class AIChatViewModel: ObservableObject {
    @Published var messages: [AIMessage] = []
    @Published var inputText: String = ""
    @Published var isLoading: Bool = false
    @Published var error: String?

    private let service = OllamaAIService.shared

    init() {

        addSystemMessage("Eres un asistente de IA integrado en una app iOS nativa. Responde siempre en español, de forma clara, concisa y útil.")
    }

    func addSystemMessage(_ content: String) {
        messages.append(AIMessage(role: .system, content: content))
    }

    func send() async {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isLoading else { return }

        inputText = ""
        isLoading = true
        error = nil

        let userMsg = AIMessage(role: .user, content: text)
        messages.append(userMsg)

        do {
            let reply = try await service.sendMessage(messages)
            let assistantMsg = AIMessage(role: .assistant, content: reply)
            messages.append(assistantMsg)
        } catch let err as AIError {
            self.error = err.errorDescription
            if messages.last?.id == userMsg.id {
                messages.removeLast()
            }
        } catch {
            self.error = error.localizedDescription
            if messages.last?.id == userMsg.id {
                messages.removeLast()
            }
        }

        isLoading = false
    }
}
