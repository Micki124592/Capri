using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Windows.Forms;

namespace CapriDesktop
{
    internal sealed class DesktopBlast : Overlay
    {
        private readonly Timer timer = new Timer();
        private readonly Stopwatch time = Stopwatch.StartNew();
        private readonly Point at;
        internal DesktopBlast(Point center) : base("Capri explosion")
        {
            ClickThrough = true;
            at = new Point(center.X - 360, center.Y - 360);
            timer.Interval = 33;
            timer.Tick += delegate
            {
                double progress = time.Elapsed.TotalSeconds / 1.4;
                if (progress >= 1) { Close(); return; }
                Present(Frame(progress), at);
            };
        }
        internal void Open() { Present(Frame(0.02), at); timer.Start(); }
        internal static Bitmap Frame(double progress)
        {
            Bitmap image = new Bitmap(720, 720, System.Drawing.Imaging.PixelFormat.Format32bppPArgb);
            if (progress < 0 || progress >= 1) return image;
            float radius = (float)(18 + 310 * (1 - Math.Pow(1 - progress, 3)));
            int alpha = (int)(230 * (1 - progress));
            using (Graphics graphics = Graphics.FromImage(image))
            {
                graphics.SmoothingMode = SmoothingMode.AntiAlias;
                using (Pen ring = new Pen(Color.FromArgb(alpha, 255, 25, 65), (float)(3 + 18 * (1 - progress)))) graphics.DrawEllipse(ring, 360 - radius, 360 - radius, radius * 2, radius * 2);
                using (Pen core = new Pen(Color.FromArgb(alpha, 255, 180, 50), 3)) graphics.DrawEllipse(core, 360 - radius * 0.7f, 360 - radius * 0.7f, radius * 1.4f, radius * 1.4f);
                for (int i = 0; i < 28; i++)
                {
                    double angle = i * Math.PI * 2 / 28;
                    float distance = radius * (0.65f + (i % 4) * 0.1f);
                    float x = 360 + (float)Math.Cos(angle) * distance, y = 360 + (float)Math.Sin(angle) * distance;
                    using (Pen shard = new Pen(Color.FromArgb(alpha, i % 3 == 0 ? Color.Cyan : Color.FromArgb(255, 55, 90)), 3)) graphics.DrawLine(shard, x, y, x + (float)Math.Cos(angle) * 26, y + (float)Math.Sin(angle) * 26);
                }
            }
            return image;
        }
        protected override void Dispose(bool disposing) { if (disposing) { timer.Stop(); timer.Dispose(); } base.Dispose(disposing); }
    }
    // Her crash is a five-second Windows blue screen, with a quick way out and no PC changes (Lachy, 2 Nov). It looks like Windows'
    // own (Lachy, 3 Nov: "It should be like this instead!"), with her asleep on its first line as if she crashed too (his pick of
    // three mockups, 3 Nov). Her stop code is the tell, and when she owns up she glitches where she lies and her ":(" turns to ":)".
    internal sealed class ChaosScreen : Overlay
    {
        internal const double Seconds = 5, RevealAt = 3.7;
        internal const string StopCode = "CHOOM_OVERFLOW", Failed = "capri.sys", Punchline = "jk. you're fine, choom";
        // Windows' wording, broken where it breaks at 1080p.
        private static readonly string[] Message = { "Your PC ran into a problem and needs to restart. We're", "just collecting some error info, and then we'll restart for", "you." };
        private static readonly Color Blue = Color.FromArgb(0, 120, 215);
        // Where she lies on that 1080p screen: the middle of her lying cells, their top and her size. Her legs hang off the end of
        // the first line.
        internal const float LieMiddle = 1030, LieTop = 196, LieSize = 1.4f;
        private const double FrameTime = 0.08;
        private readonly Rectangle room;
        private readonly Stopwatch clock;
        private readonly Timer ticks = new Timer();
        private readonly Func<bool> blocked;
        private readonly Clip rest, glitch, talk;
        private Native.Surface surface;
        private double born, drawAt;
        internal readonly bool Motion;
        internal int Percent;
        internal bool Revealed;
        internal Func<bool> EscDown = delegate { return (Native.GetAsyncKeyState(0x1B) & 0x8000) != 0; };

        // She sleeps in rest, glitches once in glitch when she owns up, then talks in talk: her lying clips.
        internal ChaosScreen(Rectangle area, Stopwatch time, bool motion, Func<bool> fullScreen, Clip rest, Clip glitch, Clip talk) : base("Capri fake crash")
        {
            room = area;
            clock = time;
            Motion = motion;
            blocked = fullScreen;
            this.rest = rest;
            this.glitch = glitch;
            this.talk = talk;
            ticks.Interval = 40;
            ticks.Tick += delegate
            {
                try { Advance(clock.Elapsed.TotalSeconds); }
                catch (Exception) { Close(); }
            };
        }

        internal Bitmap Canvas { get { return surface == null ? null : surface.Image; } }
        internal bool Running { get { return ticks.Enabled; } }
        internal string Said { get { return "CAPRI // CHAOS\nFAKE CRASH\n" + (Revealed ? ":)" : ":(") + "\n" + string.Join(" ", Message) + "\n" + (Revealed ? Punchline : Percent + "% complete") + "\nFor more information about this issue and possible fixes, visit https://www.windows.com/stopcode\nIf you call a support person, give them this info:\nStop code: " + StopCode + "\nWhat failed: " + Failed + "\nclick anywhere or press esc"; } }

        internal bool Open()
        {
            if (blocked()) return false;
            Bounds = room;
            surface = new Native.Surface(room.Width, room.Height);
            born = clock.Elapsed.TotalSeconds;
            Percent = Motion ? 0 : 100;
            Revealed = !Motion;
            Render(0);
            if (blocked()) return false;
            Show();
            Native.SetWindowPos(Handle, new IntPtr(-1), room.X, room.Y, room.Width, room.Height, 0x0010);
            ticks.Start();
            return true;
        }

        internal void Advance(double now)
        {
            if (IsDisposed || surface == null) return;
            double passed = Math.Max(0, now - born);
            if (EscDown() || blocked() || passed >= Seconds) { Close(); return; }
            if (!Motion) return;
            bool revealed = passed >= RevealAt;
            if (Revealed == revealed && now < drawAt) return;
            Percent = Math.Min(100, (int)(100 * passed / RevealAt));
            Revealed = revealed;
            drawAt = now + FrameTime;
            Render(passed);
        }

        // She stays out of keyboard focus, so Esc is polled as well as handled here. Every mouse button dismisses her plate.
        protected override void WndProc(ref Message message)
        {
            int kind = message.Msg;
            if ((kind == 0x0100 || kind == 0x0104) && message.WParam.ToInt32() == (int)Keys.Escape
                || kind == 0x0201 || kind == 0x0203 || kind == 0x0204 || kind == 0x0206 || kind == 0x0207 || kind == 0x0209 || kind == 0x020B || kind == 0x020D
                || kind == 0x00A1 || kind == 0x00A3 || kind == 0x00A4 || kind == 0x00A6 || kind == 0x00A7 || kind == 0x00A9 || kind == 0x00AB || kind == 0x00AD)
            {
                message.Result = new IntPtr(1);
                Close();
                return;
            }
            if (kind == 0x0084) { message.Result = new IntPtr(1); return; }
            if (kind == 0x0021) { message.Result = new IntPtr(3); return; }
            if (kind == 0x02E0) return;
            base.WndProc(ref message);
        }

        // Laid out on Windows' own 1080p screen and scaled to fit hers. Owning up splits her ":)" and joke into her colours.
        private void Render(double passed)
        {
            using (Graphics graphics = Graphics.FromImage(surface.Image))
            {
                graphics.Clear(Blue);
                float s = Math.Min(room.Width / 1920f, room.Height / 1080f);
                graphics.TranslateTransform((room.Width - 1920 * s) / 2, (room.Height - 1080 * s) / 2);
                graphics.ScaleTransform(s, s);
                graphics.TextRenderingHint = TextRenderingHint.AntiAlias;
                StringFormat exact = StringFormat.GenericTypographic;
                using (Font face = new Font("Segoe UI Light", 195, GraphicsUnit.Pixel))
                using (Font text = new Font("Segoe UI Light", 43, GraphicsUnit.Pixel))
                using (Font small = new Font("Segoe UI", 19, GraphicsUnit.Pixel))
                using (Brush white = new SolidBrush(Color.White))
                using (Brush hair = new SolidBrush(HudPaint.Hair))
                using (Brush pink = new SolidBrush(Color.FromArgb(230, HudPaint.Hair)))
                using (Brush green = new SolidBrush(Color.FromArgb(230, HudPaint.Skin)))
                using (Brush ink = new SolidBrush(Blue))
                {
                    if (Revealed) Split(graphics, ":)", face, hair, pink, green, 181, 123, 7);
                    else graphics.DrawString(":(", face, white, 181, 123, exact);
                    for (int line = 0; line < Message.Length; line++) graphics.DrawString(Message[line], text, white, 208, 388 + 64 * line, exact);
                    if (Revealed) Split(graphics, Punchline, text, white, pink, green, 208, 606, 3);
                    else graphics.DrawString(Percent + "% complete", text, white, 208, 606, exact);
                    graphics.FillRectangle(white, 208, 700, 108, 108);
                    Code(graphics, ink, 208, 700, 4);
                    graphics.DrawString("For more information about this issue and possible fixes, visit https://www.windows.com/stopcode", small, white, 343, 692, exact);
                    graphics.DrawString("If you call a support person, give them this info:", small, white, 343, 760, exact);
                    graphics.DrawString("Stop code: " + StopCode, small, white, 343, 794, exact);
                    graphics.DrawString("What failed: " + Failed, small, white, 343, 828, exact);
                    if (Revealed) graphics.DrawString("click anywhere or press esc", small, white, 343, 882, exact);
                }
                Her(graphics, passed);
            }
            surface.Show(Handle, room.Location);
        }

        private static void Split(Graphics graphics, string words, Font font, Brush top, Brush left, Brush right, float x, float y, float apart)
        {
            StringFormat exact = StringFormat.GenericTypographic;
            graphics.DrawString(words, font, left, x - apart, y, exact);
            graphics.DrawString(words, font, right, x + apart, y + 1, exact);
            graphics.DrawString(words, font, top, x, y, exact);
        }

        // Her, over the first line: asleep until she owns up, then one glitch where she lies, then talking. Reduced motion
        // keeps her still on her first talking frame.
        private void Her(Graphics graphics, double passed)
        {
            double owned = passed - RevealAt, glitched = glitch.Count / glitch.Fps;
            Clip clip = !Revealed ? rest : Motion && owned < glitched ? glitch : talk;
            double into = !Revealed ? passed : !Motion ? 0 : clip == glitch ? owned : owned - glitched;
            Bitmap strip = clip.Strip;
            if (strip == null) return;
            int wide = strip.Width / clip.Count, index = (int)(Math.Max(0, into) * clip.Fps) % clip.Count;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            graphics.DrawImage(strip, new RectangleF(LieMiddle - wide * LieSize / 2, LieTop, wide * LieSize, strip.Height * LieSize), new RectangleF(wide * index, 0, wide, strip.Height), GraphicsUnit.Pixel);
        }

        // A QR code's look, 25 cells with a one-cell white border: its three corner squares, timing lines and fixed noise. It
        // doesn't scan to anything.
        private static void Code(Graphics graphics, Brush ink, float x, float y, float cell)
        {
            const int cells = 25;
            Random noise = new Random(51);
            for (int row = 0; row < cells; row++)
                for (int col = 0; col < cells; col++)
                {
                    bool coin = noise.Next(2) == 0;
                    int r = row < 8 ? row : row >= cells - 8 ? row - (cells - 7) : -9;
                    int c = col < 8 ? col : col >= cells - 8 ? col - (cells - 7) : -9;
                    bool dark;
                    if (r != -9 && c != -9 && !(row >= cells - 8 && col >= cells - 8))
                        dark = r >= 0 && r <= 6 && c >= 0 && c <= 6 && (r == 0 || r == 6 || c == 0 || c == 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4));
                    else if (row == 6 || col == 6) dark = (row + col) % 2 == 0;
                    else dark = coin;
                    if (dark) graphics.FillRectangle(ink, x + (col + 1) * cell, y + (row + 1) * cell, cell, cell);
                }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                ticks.Dispose();
                if (surface != null) { surface.Dispose(); surface = null; }
            }
            base.Dispose(disposing);
        }
    }

    internal sealed partial class Companion
    {
        private bool livelyChaos;
        private int mischiefStep;
        private double nextMischief, danceUntil = -1, nextCrash;
        private DesktopBlast desktopBlast;

        private void StartMischief(double now)
        {
            livelyChaos = true;
            mischiefStep = 0;
            nextMischief = now + 2;
            nextCrash = now + 35;
            Brain.Log("chaos animation", -1, "started dance, stroll, explosion and glitch cycle");
        }

        private void CheckMischief(double now)
        {
            if (!Program.Capricorn || !livelyChaos || quitting || hidden || Hushed || !preferences.Motion || ChaosHeld() || chaosScreen != null || typer.Visible || menu.Visible) return;
            if (danceUntil >= 0)
            {
                if (now < danceUntil) return;
                Animate("stop_dancing");
                danceUntil = -1;
            }
            if (now >= nextCrash)
            {
                if (ShowCrash()) { nextCrash = now + 180; Brain.Log("chaos animation", -1, "fake blue screen shown"); }
                return;
            }
            if (now < nextMischief || dragging || pressed || falling || riding || hanging || onPanel || walking) return;
            if (speech != null || talkState != TalkState.Off) return;
            nextMischief = now + 12 + random.NextDouble() * 8;
            int action = mischiefStep++ % 4;
            if (action == 0) { Animate("dance"); danceUntil = now + 8; }
            else if (action == 1) { if (!Stroll()) Animate("grin"); }
            else if (action == 2)
            {
                if (desktopBlast != null) desktopBlast.Close();
                desktopBlast = new DesktopBlast(new Point(anchor.X + Width / 2, anchor.Y + Height / 2));
                desktopBlast.Open();
                Static(true, Cue.Hop, true);
                Animate("grin");
            }
            else { Animate("glitch"); Static(true, Cue.Hop, true); }
            Brain.Log("chaos animation", -1, new[] { "dance", "stroll", "explosion", "glitch" }[action]);
        }

        private void StopMischief()
        {
            livelyChaos = false;
            if (danceUntil >= 0) { Animate("stop_dancing"); danceUntil = -1; }
            if (desktopBlast != null) { desktopBlast.Close(); desktopBlast = null; }
        }
        private ChaosScreen chaosScreen;
        private double chaosScreenAt = double.PositiveInfinity;
        private bool chaosScreenUsed;

        // The clones' own session owns her one prank. Full screen or a stream delays it, as with her other chaos acts, and stopping
        // them cancels it entirely.
        private void ChaosTick(double now)
        {
            if (cloneWindow == null || quitting) { ChaosOff(); return; }
            if (chaosScreen != null || chaosScreenUsed || now < chaosScreenAt - 5) return;
            bool held = ChaosHeld();
            // Her lying art loads off her thread in the seconds before, so the crash doesn't hitch as it opens.
            if (!held) { laydownSleep.Warm(false); laydownGlitch.Warm(false); laydownTalk.Warm(false); }
            if (held || now < chaosScreenAt) return;
            ShowCrash();
        }

        // fake_crash: her fake blue screen when they ask, clones or not, never over full screen, a stream or a mouse job; stop
        // closes it.
        private string CrashTool(string action)
        {
            if (InvokeRequired) return (string)Invoke(new Func<string, string>(CrashTool), action);
            if (action == "stop") { ChaosOff(); return "Stopped my fake blue screen."; }
            if (chaosScreen != null) return "My fake blue screen is already showing.";
            if (quitting || ChaosHeld()) return "I can't show that during full screen, a stream or a mouse job.";
            return ShowCrash() ? "Showing my fake blue screen." : "Couldn't show my fake blue screen.";
        }

        // Opens her fake blue screen on the monitor she's on, and says whether it opened.
        private bool ShowCrash()
        {
            Point centre = new Point(anchor.X + Width / 2, anchor.Y + Height / 2);
            ChaosScreen opened = new ChaosScreen(Screen.FromPoint(centre).Bounds, clock, preferences.Motion, ChaosHeld, laydownSleep, laydownGlitch, laydownTalk);
            try
            {
                if (!opened.Open()) { opened.Dispose(); return false; }
                chaosScreenUsed = true;
                chaosScreen = opened;
                opened.FormClosed += delegate { if (chaosScreen == opened) chaosScreen = null; };
                return true;
            }
            catch (Exception)
            {
                chaosScreenUsed = true;
                opened.Dispose();
                return false;
            }
        }

        // What holds her fake crash back, and closes it if it starts while it's up: full screen, a stream, a job of hers (its
        // clicks would land on the plate), their cursor lent to her, or her being dragged (a Codex review, 2 Nov).
        private bool ChaosHeld()
        {
            return FullScreen() || Computer.Streaming || working != null || MidJob(job) || lentOut || dragging || pressed;
        }

        private void ChaosOff()
        {
            chaosScreenAt = double.PositiveInfinity;
            chaosScreenUsed = false;
            ChaosScreen closing = chaosScreen;
            chaosScreen = null;
            if (closing != null) closing.Close();
        }
    }
}
